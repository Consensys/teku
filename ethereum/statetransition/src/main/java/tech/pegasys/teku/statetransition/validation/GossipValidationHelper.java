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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipValidationHelper {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final int maxOffsetTimeInMillis;
  private final AttestationStateSelector attestationStateSelector;

  public GossipValidationHelper(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.maxOffsetTimeInMillis = spec.getNetworkingConfig().getMaximumGossipClockDisparity();
    this.attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, metricsSystem);
  }

  public boolean isSlotFinalized(final UInt64 slot) {
    final UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return slot.isLessThanOrEqualTo(finalizedSlot);
  }

  public boolean isBeforeFinalizedSlot(final UInt64 slot) {
    final UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return slot.isLessThan(finalizedSlot);
  }

  public boolean isSlotFromFuture(final UInt64 slot) {
    final ReadOnlyStore store = recentChainData.getStore();
    final UInt64 maxTime = store.getTimeInMillis().plus(maxOffsetTimeInMillis);
    final UInt64 maxCurrSlot =
        spec.getCurrentSlotFromTimeMillis(maxTime, store.getGenesisTimeMillis());
    return slot.isGreaterThan(maxCurrSlot);
  }

  public boolean isSlotCurrent(final UInt64 slot) {
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 slotEndTimeMillis = slotStartTimeMillis.plus(spec.getSlotDurationMillis(slot));
    final UInt64 currentTimeMillis = getCurrentTimeMillis();
    return currentTimeMillis.isGreaterThanOrEqualTo(
            slotStartTimeMillis.minusMinZero(maxOffsetTimeInMillis))
        && currentTimeMillis.isLessThanOrEqualTo(slotEndTimeMillis.plus(maxOffsetTimeInMillis));
  }

  public boolean isSignatureValidWithRespectToBuilderIndex(
      final Bytes signingRoot,
      final UInt64 builderIndex,
      final BLSSignature signature,
      final BeaconState state) {
    return spec.getBuilderPubKey(state, builderIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final Bytes signingRoot,
      final UInt64 proposerIndex,
      final BLSSignature signature,
      final BeaconState state) {
    return spec.getValidatorPubKey(state, proposerIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final DataColumnSidecarUtil.SignatureVerificationData signatureVerificationData) {
    return isSignatureValidWithRespectToProposerIndex(
        signatureVerificationData.signingRoot(),
        signatureVerificationData.proposerIndex(),
        signatureVerificationData.signature(),
        signatureVerificationData.state());
  }

  /**
   * Retrieve the state for the parent block, applying the epoch transition if required to be able
   * to calculate the expected proposer for block.
   */
  public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final UInt64 parentBlockSlot, final Bytes32 parentBlockRoot, final UInt64 slot) {
    final UInt64 firstSlotInBlockEpoch =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot));
    return parentBlockSlot.isLessThan(firstSlotInBlockEpoch)
        ? recentChainData.retrieveBlockState(
            new SlotAndBlockRoot(firstSlotInBlockEpoch, parentBlockRoot))
        : recentChainData.retrieveBlockState(parentBlockRoot);
  }

  public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final DataColumnSidecarUtil.StateRetrievalData stateRetrievalData) {
    return getParentStateInBlockEpoch(
        stateRetrievalData.parentBlockSlot(),
        stateRetrievalData.parentBlockRoot(),
        stateRetrievalData.slot());
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return recentChainData.retrieveBlockState(slotAndBlockRoot);
  }

  public boolean currentFinalizedCheckpointIsAncestorOfBlock(
      final UInt64 blockSlot, final Bytes32 blockParentRoot) {
    return spec.blockDescendsFromLatestFinalizedBlock(
        blockSlot,
        blockParentRoot,
        recentChainData.getStore(),
        recentChainData.getForkChoiceStrategy().orElseThrow());
  }

  public boolean isProposerTheExpectedProposer(
      final UInt64 proposerIndex, final UInt64 slot, final BeaconState postState) {
    final int expectedProposerIndex = spec.getBeaconProposerIndex(postState, slot);
    return expectedProposerIndex == proposerIndex.longValue();
  }

  public boolean isProposerTheExpectedProposer(
      final DataColumnSidecarUtil.ProposerValidationData proposerValidationData) {
    return isProposerTheExpectedProposer(
        proposerValidationData.proposerIndex(),
        proposerValidationData.slot(),
        proposerValidationData.postState());
  }

  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 blockRoot) {
    return recentChainData.getSlotForBlockRoot(blockRoot);
  }

  public boolean isBlockAvailable(final Bytes32 blockRoot) {
    return recentChainData.containsBlock(blockRoot);
  }

  int getMaxOffsetTimeInMillis() {
    return maxOffsetTimeInMillis;
  }

  public SafeFuture<Optional<BeaconState>> getStateForAttestationValidation(
      final AttestationData attestationData) {
    return attestationStateSelector.getStateToValidate(attestationData);
  }

  public UInt64 getGenesisTime() {
    return recentChainData.getGenesisTime();
  }

  public UInt64 getCurrentTimeMillis() {
    return recentChainData.getStore().getTimeInMillis();
  }

  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return recentChainData.getForkChoiceStrategy().orElseThrow();
  }

  public SafeFuture<Optional<BeaconBlock>> retrieveBlockByRoot(final Bytes32 root) {
    return recentChainData.retrieveBlockByRoot(root);
  }

  public boolean isActiveBuilder(
      final UInt64 builderIndex, final BeaconState state, final UInt64 slot) {
    return MiscHelpersGloas.required(spec.atSlot(slot).miscHelpers())
        .isActiveBuilder(state, builderIndex);
  }

  public boolean isValidatorInPayloadTimelinessCommittee(
      final UInt64 validatorIndex, final BeaconState state, final UInt64 slot) {
    return spec.getPtc(state, slot).contains(validatorIndex.intValue());
  }

  public boolean isSlotCurrentOrNext(final UInt64 slot) {
    return recentChainData
        .getCurrentSlot()
        .map(currentSlot -> slot.equals(currentSlot) || slot.equals(currentSlot.plus(ONE)))
        .orElse(false);
  }

  public boolean builderHasEnoughBalanceForBid(
      final UInt64 bidValue,
      final UInt64 builderIndex,
      final BeaconState state,
      final UInt64 slot) {
    return BeaconStateAccessorsGloas.required(spec.atSlot(slot).beaconStateAccessors())
        .canBuilderCoverBid(state, builderIndex, bidValue);
  }

  public boolean isBlockHashKnown(final Bytes32 blockHash, final Bytes32 blockRoot) {
    final Optional<Bytes32> maybeBlockHash =
        recentChainData.getExecutionBlockHashForBlockRoot(blockRoot);
    return maybeBlockHash.isPresent() && blockHash.equals(maybeBlockHash.get());
  }
}
