/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.ssz.type.Bytes20;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceNotifier {
  private static final int MAX_PROPOSER_SEEN_EPOCHS = 2;
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineChannel executionEngineChannel;
  private final Spec spec;
  private final RecentChainData recentChainData;

  private final CurrentForkChoiceUpdatedInfo currentForkChoiceUpdatedInfo =
      new CurrentForkChoiceUpdatedInfo();

  private Map<UInt64, ProposerLastSeenSlotAndFeeRecipient>
      proposerIndexLastSeenSlotAndFeeRecipient = new HashMap<>();

  private Optional<UInt64> maybeLastAttestationDueSlot = Optional.empty();

  public ForkChoiceNotifier(
      final RecentChainData recentChainData,
      final ExecutionEngineChannel executionEngineChannel,
      final Spec spec) {
    this.recentChainData = recentChainData;
    this.executionEngineChannel = executionEngineChannel;
    this.spec = spec;
  }

  public void onUpdatePreparableProposers(
      Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    LOG.debug("onUpdatePreparableProposers {}", beaconPreparableProposers);

    recentChainData
        .getCurrentSlot()
        .ifPresent(
            currentSlot ->
                updatePreparableProposers(
                    beaconPreparableProposers,
                    attestationDueHasBeenCalledForSlot(currentSlot)
                        ? currentSlot.plus(1)
                        : currentSlot));
  }

  private boolean attestationDueHasBeenCalledForSlot(UInt64 slot) {
    return maybeLastAttestationDueSlot
        .map(lastAttestationDueSlot -> lastAttestationDueSlot.isGreaterThanOrEqualTo(slot))
        .orElse(false);
  }

  public void onForkChoiceUpdated(
      Bytes32 optimisticHeadBeaconBlockRoot, ForkChoiceState forkChoiceState) {
    LOG.debug("onForkChoiceUpdated {}, {}", optimisticHeadBeaconBlockRoot, forkChoiceState);

    Optional<Bytes32> currentValidatedHead =
        recentChainData.getChainHead().map(StateAndBlockSummary::getRoot);
    boolean isForkChoiceStateOnValidatedHead =
        currentValidatedHead.isPresent()
            && optimisticHeadBeaconBlockRoot.equals(currentValidatedHead.get());

    currentForkChoiceUpdatedInfo.forkChoiceState = Optional.of(forkChoiceState);
    currentForkChoiceUpdatedInfo.isOnValidatedHead = isForkChoiceStateOnValidatedHead;

    recentChainData
        .getCurrentSlot()
        .ifPresent(
            (slot) -> {
              UInt64 targetSlot = slot.plus(1);
              callExecutionEngineForkChoiceUpdated(getPayloadAttributes(targetSlot), targetSlot);
            });
  }

  public void onAttestationsDue(UInt64 slot) {
    LOG.debug("onAttestationsDue {}", slot);

    maybeLastAttestationDueSlot = Optional.of(slot);

    UInt64 targetSlot = slot.plus(1);

    if (currentForkChoiceUpdatedInfo.hasNotBeenCalledForSlot(targetSlot)) {

      Optional<PayloadAttributes> payloadAttributes = getPayloadAttributes(targetSlot);

      if (payloadAttributes.isPresent()) {
        callExecutionEngineForkChoiceUpdated(payloadAttributes, targetSlot);
      }
    }
  }

  public CurrentForkChoiceUpdatedInfo getCurrentForkChoiceUpdatedInfo() {
    return currentForkChoiceUpdatedInfo;
  }

  private void updatePreparableProposers(
      Collection<BeaconPreparableProposer> beaconPreparableProposers, UInt64 currentSlot) {

    final int maxProposerSeenSlot = spec.getSlotsPerEpoch(currentSlot) * MAX_PROPOSER_SEEN_EPOCHS;
    boolean newProposerIndexAdded = false;

    // remove expired proposers
    proposerIndexLastSeenSlotAndFeeRecipient =
        proposerIndexLastSeenSlotAndFeeRecipient.entrySet().stream()
            .filter(p -> p.getValue().lastSeenSlot.compareTo(currentSlot) < maxProposerSeenSlot)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // update or add new proposers
    for (BeaconPreparableProposer beaconPreparableProposer : beaconPreparableProposers) {
      ProposerLastSeenSlotAndFeeRecipient current =
          proposerIndexLastSeenSlotAndFeeRecipient.get(
              beaconPreparableProposer.getValidatorIndex());
      if (current == null) {
        proposerIndexLastSeenSlotAndFeeRecipient.put(
            beaconPreparableProposer.getValidatorIndex(),
            new ProposerLastSeenSlotAndFeeRecipient(
                currentSlot, beaconPreparableProposer.getFeeRecipient()));

        newProposerIndexAdded = true;

      } else {
        current.lastSeenSlot = currentSlot;
      }
    }

    if (newProposerIndexAdded) {
      final Optional<PayloadAttributes> payloadAttributes = getPayloadAttributes(currentSlot);

      if (payloadAttributes.isPresent())
        callExecutionEngineForkChoiceUpdated(payloadAttributes, currentSlot);
    }
  }

  /**
   * checks proposal preconditions and if we have a prepared proposer required to produce a block
   * for the targetSlot
   *
   * <p>if conditions are met, builds a {@link PayloadAttributes} gathering data from current chain
   * head state.
   *
   * @param targetSlot at which we want to produce a block
   * @return optionally payload attributes for the block proposer due
   */
  private Optional<PayloadAttributes> getPayloadAttributes(UInt64 targetSlot) {
    if (!currentForkChoiceUpdatedInfo.isPossibleToPropose()) {
      return Optional.empty();
    }

    return recentChainData
        .getChainHead()
        .map(
            stateAndBlockSummary -> {
              UInt64 proposerIndex =
                  UInt64.valueOf(
                      spec.getBeaconProposerIndex(stateAndBlockSummary.getState(), targetSlot));

              ProposerLastSeenSlotAndFeeRecipient maybePreparedProposer =
                  proposerIndexLastSeenSlotAndFeeRecipient.get(proposerIndex);

              if (maybePreparedProposer != null) {

                final BeaconState state = stateAndBlockSummary.getState();
                final UInt64 epoch = spec.computeEpochAtSlot(targetSlot);

                return new PayloadAttributes(
                    spec.computeTimeAtSlot(state, targetSlot),
                    spec.atEpoch(epoch).beaconStateAccessors().getRandaoMix(state, epoch),
                    maybePreparedProposer.feeRecipient);
              }
              return null;
            });
  }

  private void callExecutionEngineForkChoiceUpdated(
      final Optional<PayloadAttributes> payloadAttributes,
      final UInt64 executionPayloadTargetSlot) {

    currentForkChoiceUpdatedInfo.calledAtSlot = Optional.of(executionPayloadTargetSlot);
    currentForkChoiceUpdatedInfo.payloadAttributes = payloadAttributes;

    ForkChoiceState forkChoiceState =
        currentForkChoiceUpdatedInfo.forkChoiceState.orElseThrow(
            () -> new IllegalStateException("A current ForkChoiceState is expected"));

    // do we need to .join() or ordering will be respected at this point?
    // I think it is currently synchronous up to the actual call
    // or maybe if it is now a real channel is not the case.
    executionEngineChannel
        .forkChoiceUpdated(forkChoiceState, payloadAttributes)
        .finish(
            result -> {
              currentForkChoiceUpdatedInfo.result = Optional.of(result);
              LOG.info("forkChoiceUpdated result: {}", result);
            },
            error ->
                LOG.error(
                    "Error while calling forkChoiceUpdated. Message: {}", error.getMessage()));
  }

  @VisibleForTesting
  Bytes20 getProposerIndexFeeRecipient(UInt64 proposerIndex) {
    ProposerLastSeenSlotAndFeeRecipient lastSeenSlotAndFeeRecipient =
        proposerIndexLastSeenSlotAndFeeRecipient.get(proposerIndex);
    return lastSeenSlotAndFeeRecipient != null ? lastSeenSlotAndFeeRecipient.feeRecipient : null;
  }

  @VisibleForTesting
  UInt64 getProposerIndexLastSeenSlot(UInt64 proposerIndex) {
    ProposerLastSeenSlotAndFeeRecipient lastSeenSlotAndFeeRecipient =
        proposerIndexLastSeenSlotAndFeeRecipient.get(proposerIndex);
    return lastSeenSlotAndFeeRecipient != null ? lastSeenSlotAndFeeRecipient.lastSeenSlot : null;
  }

  private static class ProposerLastSeenSlotAndFeeRecipient {
    UInt64 lastSeenSlot;
    Bytes20 feeRecipient;

    public ProposerLastSeenSlotAndFeeRecipient(UInt64 lastSeenSlot, Bytes20 feeRecipient) {
      this.lastSeenSlot = lastSeenSlot;
      this.feeRecipient = feeRecipient;
    }
  }

  public static class CurrentForkChoiceUpdatedInfo {
    private Optional<ForkChoiceState> forkChoiceState = Optional.empty();
    private Boolean isOnValidatedHead = false;
    private Optional<UInt64> calledAtSlot = Optional.empty();
    private Optional<PayloadAttributes> payloadAttributes = Optional.empty();
    private Optional<ForkChoiceUpdatedResult> result = Optional.empty();

    private boolean isPossibleToPropose() {
      return forkChoiceState.isPresent() && isOnValidatedHead;
    }

    private boolean hasNotBeenCalledForSlot(UInt64 slot) {
      return calledAtSlot.map(lastSlot -> lastSlot.compareTo(slot) < 0).orElse(true);
    }

    public Optional<UInt64> getCalledAtSlot() {
      return calledAtSlot;
    }

    public Optional<ForkChoiceState> getForkChoiceState() {
      return forkChoiceState;
    }

    public Boolean getOnValidatedHead() {
      return isOnValidatedHead;
    }

    public Optional<PayloadAttributes> getPayloadAttributes() {
      return payloadAttributes;
    }

    public Optional<ForkChoiceUpdatedResult> getResult() {
      return result;
    }
  }
}
