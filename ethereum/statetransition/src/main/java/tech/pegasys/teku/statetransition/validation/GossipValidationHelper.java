/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
    return isSignatureValidForIndex(signingRoot, builderIndex, signature, state);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final Bytes signingRoot,
      final UInt64 proposerIndex,
      final BLSSignature signature,
      final BeaconState state) {
    return isSignatureValidForIndex(signingRoot, proposerIndex, signature, state);
  }

  private boolean isSignatureValidForIndex(
      final Bytes signingRoot,
      final UInt64 validatorIndex,
      final BLSSignature signature,
      final BeaconState state) {
    return spec.getValidatorPubKey(state, validatorIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
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
        ? recentChainData.retrieveStateAtSlot(
            new SlotAndBlockRoot(firstSlotInBlockEpoch, parentBlockRoot))
        : recentChainData.retrieveBlockState(parentBlockRoot);
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

  public boolean isValidBuilderIndex(
      final UInt64 builderIndex, final BeaconState state, final UInt64 slot) {
    if (builderIndex.isGreaterThanOrEqualTo(state.getValidators().size())
        || builderIndex.intValue() < 0) {
      return false;
    }
    final int index = builderIndex.intValue();
    final Validator builder = state.getValidators().get(index);
    final boolean isActiveBuilder =
        spec.atSlot(slot).predicates().isActiveValidator(builder, spec.computeEpochAtSlot(slot));
    return !builder.isSlashed() && isActiveBuilder;
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return recentChainData.retrieveStateAtSlot(slotAndBlockRoot);
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

  public boolean hasBuilderWithdrawalCredential(
      final UInt64 builderIndex, final BeaconState state, final UInt64 slot) {
    return MiscHelpersGloas.required(spec.atSlot(slot).miscHelpers())
        .hasBuilderWithdrawalCredential(state.getValidators().get(builderIndex.intValue()));
  }

  public boolean builderHasEnoughBalanceForBid(
      final UInt64 value, final UInt64 builderIndex, final BeaconState state, final UInt64 slot) {
    final UInt64 builderBalance = state.getBalances().get(builderIndex.intValue()).get();
    final UInt64 minActivationBalance =
        SpecConfigGloas.required(spec.atSlot(slot).getConfig()).getMinActivationBalance();
    return minActivationBalance.plus(value).isLessThanOrEqualTo(builderBalance);
  }

  public boolean isBlockHashKnown(final Bytes32 blockHash, final Bytes32 blockRoot) {
    final Optional<Bytes32> maybeBlockHash =
        recentChainData.getExecutionBlockHashForBlockRoot(blockRoot);
    return maybeBlockHash.isPresent() && blockHash.equals(maybeBlockHash.get());
  }
}
