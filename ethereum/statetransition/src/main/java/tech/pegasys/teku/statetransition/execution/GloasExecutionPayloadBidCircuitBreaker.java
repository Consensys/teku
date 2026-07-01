/*
 * Copyright Consensys Software Inc., 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.execution;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GloasExecutionPayloadBidCircuitBreaker implements ExecutionPayloadBidCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final int faultInspectionWindow;
  private final int allowedFaults;
  private final int consecutiveAllowedFaults;
  private final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier;
  private final Map<Bytes32, BlockPayloadStatus> blockPayloadStatusByRoot = new HashMap<>();
  private final Map<UInt64, BuilderCircuitBreakerStatus> builderStatusByIndex = new HashMap<>();

  public GloasExecutionPayloadBidCircuitBreaker(
      final Spec spec,
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults,
      final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.spec = spec;
    this.faultInspectionWindow = faultInspectionWindow;
    this.allowedFaults = allowedFaults;
    this.consecutiveAllowedFaults = consecutiveAllowedFaults;
    this.forkChoiceStrategySupplier = forkChoiceStrategySupplier;
  }

  @Override
  public synchronized boolean isEngaged(final Bytes32 parentRoot, final BeaconState state) {
    prune(state.getSlot());
    final Optional<ReadOnlyForkChoiceStrategy> maybeForkChoiceStrategy =
        forkChoiceStrategySupplier.get();
    if (maybeForkChoiceStrategy.isEmpty()) {
      LOG.debug(
          "Gloas builder circuit breaker engaged because fork choice data is unavailable at slot {}",
          state.getSlot());
      return true;
    }

    if (inspectPayloadAvailability(maybeForkChoiceStrategy.get(), parentRoot, state)) {
      LOG.debug(
          "Gloas builder circuit breaker engaged because fork choice data is incomplete for parent root {} at slot {}",
          parentRoot,
          state.getSlot());
      return true;
    }

    LOG.debug(
        "Gloas builder circuit breaker has not engaged for parent root {} at slot {}",
        parentRoot,
        state.getSlot());
    return false;
  }

  @Override
  public synchronized boolean isBuilderAllowed(final UInt64 builderIndex, final BeaconState state) {
    prune(state.getSlot());
    return getCurrentBuilderStatus(builderIndex, state)
        .map(
            builderStatus -> {
              final boolean builderAllowed =
                  builderStatus.banUntilSlot().isLessThanOrEqualTo(state.getSlot());
              if (!builderAllowed) {
                LOG.debug(
                    "Rejecting Gloas builder index {} because it is banned until slot {} at slot {}",
                    builderIndex,
                    builderStatus.banUntilSlot(),
                    state.getSlot());
              }
              return builderAllowed;
            })
        .orElse(true);
  }

  @Override
  public synchronized void observeBlock(final SignedBeaconBlock block) {
    block
        .getMessage()
        .getBody()
        .getOptionalSignedExecutionPayloadBid()
        .ifPresent(
            signedBid ->
                recordBlockBuilder(
                    block.getRoot(), block.getSlot(), signedBid.getMessage().getBuilderIndex()));
  }

  @VisibleForTesting
  void recordBlockBuilder(final Bytes32 blockRoot, final UInt64 slot, final UInt64 builderIndex) {
    blockPayloadStatusByRoot.put(blockRoot, new BlockPayloadStatus(slot, builderIndex));
  }

  private boolean inspectPayloadAvailability(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 parentRoot,
      final BeaconState state) {
    if (!forkChoiceStrategy.contains(parentRoot)) {
      return true;
    }

    final List<ObservedBlock> observedBlocks = new ArrayList<>();
    final Set<Bytes32> inspectedBlockRoots = new HashSet<>();

    final UInt64 slot = state.getSlot();
    final UInt64 firstSlotOfInspectionWindow = slot.minusMinZero(faultInspectionWindow);
    UInt64 currentSlot = slot.minusMinZero(1);
    while (currentSlot.isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
      final Optional<Bytes32> maybeAncestorRoot =
          forkChoiceStrategy.getAncestor(parentRoot, currentSlot);
      if (maybeAncestorRoot.isPresent() && inspectedBlockRoots.add(maybeAncestorRoot.get())) {
        final Bytes32 ancestorRoot = maybeAncestorRoot.get();
        final Optional<UInt64> maybeAncestorSlot = forkChoiceStrategy.blockSlot(ancestorRoot);
        if (maybeAncestorSlot.isEmpty()) {
          return true;
        }
        if (maybeAncestorSlot.get().isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
          getBlockPayloadStatus(ancestorRoot)
              .ifPresent(
                  blockPayloadStatus ->
                      observedBlocks.add(new ObservedBlock(ancestorRoot, blockPayloadStatus)));
        }
      }
      if (currentSlot.isZero()) {
        break;
      }
      currentSlot = currentSlot.minusMinZero(1);
    }

    observedBlocks.stream()
        .sorted(Comparator.comparing(ObservedBlock::slot))
        .forEach(
            observedBlock -> {
              if (hasAvailablePayload(forkChoiceStrategy, observedBlock.blockRoot())) {
                recordAvailablePayload(observedBlock, state);
              } else {
                recordUnavailablePayload(observedBlock, state);
              }
            });

    return false;
  }

  private boolean hasAvailablePayload(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 ancestorRoot) {
    return forkChoiceStrategy
        .getBlockData(ancestorRoot, PAYLOAD_STATUS_FULL)
        .map(nodeData -> nodeData.getValidationStatus() == ProtoNodeValidationStatus.VALID)
        .orElse(false);
  }

  private void recordAvailablePayload(final ObservedBlock observedBlock, final BeaconState state) {
    final boolean payloadFaultWasRecorded =
        observedBlock.blockPayloadStatus().markPayloadAvailable();
    getCurrentBuilderStatus(observedBlock.builderIndex(), state)
        .ifPresent(
            builderStatus -> {
              if (payloadFaultWasRecorded) {
                builderStatus.payloadFaults().remove(observedBlock.toPayloadFault());
              }
              builderStatus.resetConsecutiveUnavailablePayloads();
            });
  }

  private void recordUnavailablePayload(
      final ObservedBlock observedBlock, final BeaconState state) {
    final BlockPayloadStatus blockPayloadStatus = observedBlock.blockPayloadStatus();
    final Optional<BLSPublicKey> maybeBuilderPubKey =
        spec.getBuilderPubKey(state, observedBlock.builderIndex());
    if (maybeBuilderPubKey.isEmpty()) {
      return;
    }

    if (!blockPayloadStatus.markPayloadUnavailable()) {
      return;
    }

    final PayloadFault payloadFault = observedBlock.toPayloadFault();

    final BuilderCircuitBreakerStatus builderStatus =
        builderStatusByIndex.compute(
            observedBlock.builderIndex(),
            (__, existingStatus) -> {
              if (existingStatus == null
                  || !existingStatus.isForBuilder(maybeBuilderPubKey.get())) {
                return new BuilderCircuitBreakerStatus(maybeBuilderPubKey.get());
              }
              return existingStatus;
            });
    builderStatus.payloadFaults().addLast(payloadFault);
    builderStatus.incrementConsecutiveUnavailablePayloads();

    if (builderStatus.payloadFaults().size() > allowedFaults
        || builderStatus.consecutiveUnavailablePayloads() > consecutiveAllowedFaults) {
      builderStatus.banUntilSlot(state.getSlot().plus(faultInspectionWindow));
      LOG.debug(
          "Banning Gloas builder index {} until slot {} at slot {} after {} unavailable payloads in window and {} consecutive unavailable payloads; previous ban until slot {}",
          observedBlock.builderIndex(),
          builderStatus.banUntilSlot(),
          state.getSlot(),
          builderStatus.payloadFaults().size(),
          builderStatus.consecutiveUnavailablePayloads(),
          builderStatus.banUntilSlot());
    }
  }

  private Optional<BuilderCircuitBreakerStatus> getCurrentBuilderStatus(
      final UInt64 builderIndex, final BeaconState state) {
    final BuilderCircuitBreakerStatus builderStatus = builderStatusByIndex.get(builderIndex);
    if (builderStatus == null) {
      return Optional.empty();
    }

    final Optional<BLSPublicKey> maybeCurrentBuilderPubKey =
        spec.getBuilderPubKey(state, builderIndex);
    if (maybeCurrentBuilderPubKey.isEmpty()
        || !builderStatus.isForBuilder(maybeCurrentBuilderPubKey.get())) {
      LOG.debug(
          "Clearing Gloas builder circuit breaker status for builder index {} at slot {} because the index is no longer associated with the tracked builder public key",
          builderIndex,
          state.getSlot());
      builderStatusByIndex.remove(builderIndex);
      return Optional.empty();
    }

    return Optional.of(builderStatus);
  }

  private Optional<BlockPayloadStatus> getBlockPayloadStatus(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockPayloadStatusByRoot.get(blockRoot));
  }

  private void prune(final UInt64 currentSlot) {
    final UInt64 firstSlotOfInspectionWindow = currentSlot.minusMinZero(faultInspectionWindow);
    blockPayloadStatusByRoot
        .entrySet()
        .removeIf(entry -> entry.getValue().slot().isLessThan(firstSlotOfInspectionWindow));

    builderStatusByIndex
        .entrySet()
        .removeIf(
            entry -> {
              final BuilderCircuitBreakerStatus builderStatus = entry.getValue();
              builderStatus
                  .payloadFaults()
                  .removeIf(
                      payloadFault -> {
                        return payloadFault.slot().isLessThan(firstSlotOfInspectionWindow);
                      });
              if (builderStatus.payloadFaults().isEmpty()) {
                builderStatus.resetConsecutiveUnavailablePayloads();
              }
              if (!builderStatus.banUntilSlot().isZero()
                  && builderStatus.banUntilSlot().isLessThanOrEqualTo(currentSlot)) {
                LOG.debug(
                    "Unbanning Gloas builder index {} at slot {}; ban expired at slot {}",
                    entry.getKey(),
                    currentSlot,
                    builderStatus.banUntilSlot());
                builderStatus.clearBan();
              }
              return builderStatus.payloadFaults().isEmpty()
                  && builderStatus.consecutiveUnavailablePayloads() == 0
                  && builderStatus.banUntilSlot().isZero();
            });
  }

  private record ObservedBlock(Bytes32 blockRoot, BlockPayloadStatus blockPayloadStatus) {
    private UInt64 slot() {
      return blockPayloadStatus.slot();
    }

    private UInt64 builderIndex() {
      return blockPayloadStatus.builderIndex();
    }

    private PayloadFault toPayloadFault() {
      return new PayloadFault(blockRoot, slot(), builderIndex());
    }
  }

  private record PayloadFault(Bytes32 blockRoot, UInt64 slot, UInt64 builderIndex) {}

  private static class BlockPayloadStatus {
    private final UInt64 slot;
    private final UInt64 builderIndex;
    private boolean payloadFaultRecorded;

    private BlockPayloadStatus(final UInt64 slot, final UInt64 builderIndex) {
      this.slot = slot;
      this.builderIndex = builderIndex;
    }

    private UInt64 slot() {
      return slot;
    }

    private UInt64 builderIndex() {
      return builderIndex;
    }

    private boolean markPayloadUnavailable() {
      if (payloadFaultRecorded) {
        return false;
      }
      payloadFaultRecorded = true;
      return true;
    }

    private boolean markPayloadAvailable() {
      final boolean wasUnavailable = payloadFaultRecorded;
      payloadFaultRecorded = false;
      return wasUnavailable;
    }
  }

  private static class BuilderCircuitBreakerStatus {
    private final BLSPublicKey builderPubKey;
    private final Deque<PayloadFault> payloadFaults = new ArrayDeque<>();
    private int consecutiveUnavailablePayloads = 0;
    private UInt64 banUntilSlot = UInt64.ZERO;

    private BuilderCircuitBreakerStatus(final BLSPublicKey builderPubKey) {
      this.builderPubKey = builderPubKey;
    }

    private boolean isForBuilder(final BLSPublicKey builderPubKey) {
      return this.builderPubKey.equals(builderPubKey);
    }

    private Deque<PayloadFault> payloadFaults() {
      return payloadFaults;
    }

    private int consecutiveUnavailablePayloads() {
      return consecutiveUnavailablePayloads;
    }

    private void incrementConsecutiveUnavailablePayloads() {
      consecutiveUnavailablePayloads++;
    }

    private void resetConsecutiveUnavailablePayloads() {
      consecutiveUnavailablePayloads = 0;
    }

    private UInt64 banUntilSlot() {
      return banUntilSlot;
    }

    private void banUntilSlot(final UInt64 banUntilSlot) {
      this.banUntilSlot = banUntilSlot;
    }

    private void clearBan() {
      banUntilSlot = UInt64.ZERO;
    }
  }
}
