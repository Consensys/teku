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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GloasExecutionPayloadBidCircuitBreaker implements ExecutionPayloadBidCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();

  private final int faultInspectionWindow;
  private final int allowedFaults;
  private final int consecutiveAllowedFaults;
  private final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier;
  private final Map<Bytes32, RecentBlock> recentBlocksByRoot = new HashMap<>();
  private final Map<UInt64, BuilderCircuitBreakerStatus> builderStatusByIndex = new HashMap<>();

  public GloasExecutionPayloadBidCircuitBreaker(
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults,
      final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
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

    if (inspectPayloadAvailability(maybeForkChoiceStrategy.get(), parentRoot, state.getSlot())) {
      LOG.debug(
          "Gloas builder circuit breaker engaged because fork choice data is incomplete for parent root {} at slot {}",
          parentRoot,
          state.getSlot());
      return true;
    }

    LOG.debug("Gloas builder circuit breaker has not engaged.");
    return false;
  }

  @Override
  public synchronized boolean isBuilderAllowed(
      final UInt64 builderIndex, final UInt64 currentSlot) {
    prune(currentSlot);
    final BuilderCircuitBreakerStatus builderStatus = builderStatusByIndex.get(builderIndex);
    return builderStatus == null || builderStatus.banUntilSlot().isLessThanOrEqualTo(currentSlot);
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
    recentBlocksByRoot.put(blockRoot, new RecentBlock(slot, builderIndex));
  }

  private boolean inspectPayloadAvailability(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 parentRoot,
      final UInt64 slot) {
    if (!forkChoiceStrategy.contains(parentRoot)) {
      return true;
    }

    final List<ObservedBlock> observedBlocks = new ArrayList<>();
    final Set<Bytes32> inspectedBlockRoots = new HashSet<>();

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
          final RecentBlock recentBlock = recentBlocksByRoot.get(ancestorRoot);
          if (recentBlock != null) {
            observedBlocks.add(
                new ObservedBlock(
                    ancestorRoot, maybeAncestorSlot.get(), recentBlock.builderIndex()));
          }
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
                recordAvailablePayload(observedBlock);
              } else {
                recordUnavailablePayload(observedBlock, slot);
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

  private void recordAvailablePayload(final ObservedBlock observedBlock) {
    final RecentBlock recentBlock = recentBlocksByRoot.get(observedBlock.blockRoot());
    final BuilderCircuitBreakerStatus builderStatus =
        builderStatusByIndex.get(observedBlock.builderIndex());
    if (recentBlock == null || builderStatus == null) {
      return;
    }

    final PayloadFault existingFault = recentBlock.clearPayloadFault();
    if (existingFault != null) {
      builderStatus.payloadFaults().remove(existingFault);
    }
    builderStatus.resetConsecutiveUnavailablePayloads();
    builderStatus.clearBan();
  }

  private void recordUnavailablePayload(
      final ObservedBlock observedBlock, final UInt64 currentSlot) {
    final RecentBlock recentBlock = recentBlocksByRoot.get(observedBlock.blockRoot());
    if (recentBlock == null || recentBlock.hasPayloadFault()) {
      return;
    }

    final PayloadFault payloadFault =
        new PayloadFault(
            observedBlock.blockRoot(), observedBlock.slot(), observedBlock.builderIndex());
    recentBlock.recordPayloadFault(payloadFault);

    final BuilderCircuitBreakerStatus builderStatus =
        builderStatusByIndex.computeIfAbsent(
            observedBlock.builderIndex(), __ -> new BuilderCircuitBreakerStatus());
    builderStatus.payloadFaults().addLast(payloadFault);
    builderStatus.incrementConsecutiveUnavailablePayloads();

    if (builderStatus.payloadFaults().size() > allowedFaults
        || builderStatus.consecutiveUnavailablePayloads() > consecutiveAllowedFaults) {
      builderStatus.banUntilSlot(currentSlot.plus(faultInspectionWindow));
      LOG.debug(
          "Banning Gloas builder index {} until slot {} after {} unavailable payloads in window and {} consecutive unavailable payloads",
          observedBlock.builderIndex(),
          builderStatus.banUntilSlot(),
          builderStatus.payloadFaults().size(),
          builderStatus.consecutiveUnavailablePayloads());
    }
  }

  private void prune(final UInt64 currentSlot) {
    final UInt64 firstSlotOfInspectionWindow = currentSlot.minusMinZero(faultInspectionWindow);
    recentBlocksByRoot
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
              if (builderStatus.banUntilSlot().isLessThanOrEqualTo(currentSlot)) {
                builderStatus.clearBan();
              }
              return builderStatus.payloadFaults().isEmpty()
                  && builderStatus.consecutiveUnavailablePayloads() == 0
                  && builderStatus.banUntilSlot().isZero();
            });
  }

  private record ObservedBlock(Bytes32 blockRoot, UInt64 slot, UInt64 builderIndex) {}

  private record PayloadFault(Bytes32 blockRoot, UInt64 slot, UInt64 builderIndex) {}

  private static class RecentBlock {
    private final UInt64 slot;
    private final UInt64 builderIndex;
    private PayloadFault payloadFault;

    private RecentBlock(final UInt64 slot, final UInt64 builderIndex) {
      this.slot = slot;
      this.builderIndex = builderIndex;
    }

    private UInt64 slot() {
      return slot;
    }

    private UInt64 builderIndex() {
      return builderIndex;
    }

    private boolean hasPayloadFault() {
      return payloadFault != null;
    }

    private void recordPayloadFault(final PayloadFault payloadFault) {
      this.payloadFault = payloadFault;
    }

    private PayloadFault clearPayloadFault() {
      final PayloadFault previousPayloadFault = payloadFault;
      payloadFault = null;
      return previousPayloadFault;
    }
  }

  private static class BuilderCircuitBreakerStatus {
    private final Deque<PayloadFault> payloadFaults = new ArrayDeque<>();
    private int consecutiveUnavailablePayloads = 0;
    private UInt64 banUntilSlot = UInt64.ZERO;

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
