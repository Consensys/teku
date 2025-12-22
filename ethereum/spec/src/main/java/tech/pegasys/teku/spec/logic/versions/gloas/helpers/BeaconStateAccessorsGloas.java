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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateAccessorsGloas extends BeaconStateAccessorsFulu {

  private final MiscHelpersGloas miscHelpersGloas;
  private final SchemaDefinitionsGloas schemaDefinitions;

  public static BeaconStateAccessorsGloas required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsGloas,
        "Expected %s but it was %s",
        BeaconStateAccessorsGloas.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsGloas) beaconStateAccessors;
  }

  public BeaconStateAccessorsGloas(
      final SpecConfigGloas config,
      final SchemaDefinitionsGloas schemaDefinitions,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers) {
    super(config, predicates, miscHelpers);
    this.schemaDefinitions = schemaDefinitions;
    this.miscHelpersGloas = miscHelpers;
  }

  /**
   * get_indexed_payload_attestation
   *
   * <p>Return the indexed payload attestation corresponding to ``payload_attestation``.
   */
  public IndexedPayloadAttestation getIndexedPayloadAttestation(
      final BeaconState state, final PayloadAttestation payloadAttestation) {
    final UInt64 slot = payloadAttestation.getData().getSlot();
    final IntList ptc = getPtc(state, slot);
    final SszBitvector aggregationBits = payloadAttestation.getAggregationBits();
    final IntList attestingIndices = new IntArrayList();
    for (int i = 0; i < ptc.size(); i++) {
      if (aggregationBits.isSet(i)) {
        final int index = ptc.getInt(i);
        attestingIndices.add(index);
      }
    }
    final SszUInt64List sszAttestingIndices =
        attestingIndices
            .intStream()
            .sorted()
            .mapToObj(idx -> SszUInt64.of(UInt64.valueOf(idx)))
            .collect(
                schemaDefinitions
                    .getIndexedPayloadAttestationSchema()
                    .getAttestingIndicesSchema()
                    .collector());
    return schemaDefinitions
        .getIndexedPayloadAttestationSchema()
        .create(
            sszAttestingIndices, payloadAttestation.getData(), payloadAttestation.getSignature());
  }

  /**
   * get_ptc
   *
   * <p>Get the payload timeliness committee for the given ``slot``
   */
  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Bytes32 seed =
        Hash.sha256(
            Bytes.concatenate(getSeed(state, epoch, Domain.PTC_ATTESTER), uint64ToBytes(slot)));
    final IntList indices = new IntArrayList();
    // Concatenate all committees for this slot in order
    UInt64.range(UInt64.ZERO, getCommitteeCountPerSlot(state, epoch))
        .forEach(
            i -> {
              final IntList committee = getBeaconCommittee(state, slot, i);
              indices.addAll(committee);
            });
    return MiscHelpersGloas.required(miscHelpers)
        .computeBalanceWeightedSelection(
            state, indices, seed, SpecConfigGloas.required(config).getPtcSize(), false);
  }

  /**
   * get_builder_payment_quorum_threshold
   *
   * <p>Calculate the quorum threshold for builder payments.
   */
  public UInt64 getBuilderPaymentQuorumThreshold(final BeaconState state) {
    final UInt64 perSlotBalance = getTotalActiveBalance(state).dividedBy(config.getSlotsPerEpoch());
    final UInt64 quorum = perSlotBalance.times(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_NUMERATOR);
    return quorum.dividedBy(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_DENOMINATOR);
  }

  /**
   * is_attestation_same_slot
   *
   * <p>Check if the attestation is for the block proposed at the attestation slot.
   */
  public boolean isAttestationSameSlot(final BeaconState state, final AttestationData data) {
    if (data.getSlot().isZero()) {
      return true;
    }
    final Bytes32 blockRoot = data.getBeaconBlockRoot();
    final Bytes32 slotBlockRoot = getBlockRootAtSlot(state, data.getSlot());
    final Bytes32 prevBlockRoot = getBlockRootAtSlot(state, data.getSlot().minusMinZero(1));

    return blockRoot.equals(slotBlockRoot) && !blockRoot.equals(prevBlockRoot);
  }

  @Override
  protected boolean computeIsMatchingHead(
      final boolean isMatchingTarget,
      final boolean headRootMatches,
      final AttestationData data,
      final BeaconState state) {
    if (!isMatchingTarget || !headRootMatches) {
      return false;
    }
    if (isAttestationSameSlot(state, data)) {
      checkArgument(data.getIndex().isZero(), "Index must be set to zero");
      return true;
    } else {
      final int slotIndex = data.getSlot().mod(config.getSlotsPerHistoricalRoot()).intValue();
      final boolean payloadIndex =
          BeaconStateGloas.required(state).getExecutionPayloadAvailability().get(slotIndex).get();
      return data.getIndex().intValue() == (payloadIndex ? 1 : 0);
    }
  }

  /** get_pending_balance_to_withdraw is modified to account for pending builder payments. */
  @Override
  public UInt64 getPendingBalanceToWithdraw(
      final BeaconStateElectra state, final int validatorIndex) {
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);
    final UInt64 pendingPartialWithdrawalsBalance =
        super.getPendingBalanceToWithdraw(state, validatorIndex);
    final UInt64 pendingBuilderWithdrawalsBalance =
        stateGloas.getBuilderPendingWithdrawals().stream()
            .filter(withdrawal -> withdrawal.getBuilderIndex().intValue() == validatorIndex)
            .map(BuilderPendingWithdrawal::getAmount)
            .reduce(UInt64.ZERO, UInt64::plus);
    final UInt64 pendingBuilderPaymentsBalance =
        stateGloas.getBuilderPendingPayments().stream()
            .map(BuilderPendingPayment::getWithdrawal)
            .filter(withdrawal -> withdrawal.getBuilderIndex().intValue() == validatorIndex)
            .map(BuilderPendingWithdrawal::getAmount)
            .reduce(UInt64.ZERO, UInt64::plus);
    return pendingPartialWithdrawalsBalance
        .plus(pendingBuilderWithdrawalsBalance)
        .plus(pendingBuilderPaymentsBalance);
  }

  /**
   * get_next_sync_committee_indices is refactored to use compute_balance_weighted_selection as a
   * helper for the balance-weighted sampling process.
   */
  @Override
  public IntList getNextSyncCommitteeIndices(final BeaconState state) {
    final UInt64 epoch = getCurrentEpoch(state).plus(1);
    final IntList activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    checkArgument(activeValidatorCount > 0, "Provided state has no active validators");
    final Bytes32 seed = getSeed(state, epoch, Domain.SYNC_COMMITTEE);
    return miscHelpersGloas.computeBalanceWeightedSelection(
        state, activeValidatorIndices, seed, configElectra.getSyncCommitteeSize(), true);
  }
}
