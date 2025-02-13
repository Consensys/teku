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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.BeaconStateAccessorsEip7805;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class InclusionListUtil {

  private static final UInt64 ATTESTATION_DEADLINE = UInt64.valueOf(4);

  protected final SchemaDefinitions schemaDefinitions;
  protected final BeaconStateAccessorsEip7805 beaconStateAccessors;
  protected final MiscHelpers miscHelpers;
  protected final SpecConfig specConfig;

  public InclusionListUtil(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessorsEip7805 beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  public boolean isInclusionListForCurrentOrPreviousSlot(
      final UInt64 inclusionListSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 currentSlot = miscHelpers.computeSlotAtTime(genesisTime, currentTimeMillis);
    return inclusionListSlot.equals(currentSlot) || inclusionListSlot.equals(currentSlot.minus(1));
  }

  public boolean isInclusionListWithinDeadline(
      final UInt64 inclusionListSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 currentSlot = miscHelpers.computeSlotAtTime(genesisTime, currentTimeMillis);
    final UInt64 timeInCurrentSlot = miscHelpers.computeTimeAtSlot(genesisTime, currentSlot);
    return inclusionListSlot.equals(currentSlot)
        || (inclusionListSlot.equals(currentSlot.minus(1))
            && millisToSeconds(timeInCurrentSlot).isLessThan(ATTESTATION_DEADLINE));
  }

  /** Check if ``signed_inclusion_list`` has a valid signature. */
  public boolean isValidInclusionListSignature(
      final BeaconState state, final SignedInclusionList signedInclusionList) {
    final InclusionList message = signedInclusionList.getMessage();
    final UInt64 index = message.getValidatorIndex();
    final BLSPublicKey pubkey = state.getValidators().get(index.intValue()).getPublicKey();
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(
            message,
            beaconStateAccessors.getDomain(
                state.getForkInfo(),
                Domain.DOMAIN_INCLUSION_LIST_COMMITTEE,
                miscHelpers.computeEpochAtSlot(state.getSlot())));
    return BLS.verify(pubkey, signingRoot, signedInclusionList.getSignature());
  }

  public IntList getInclusionListCommittee(final BeaconState state, final UInt64 slot) {
    return beaconStateAccessors.getInclusionListCommittee(state, slot);
  }

  // TODO EIP7805 this IntList to SszList conversion to get the HTR could be improved
  public boolean hasCorrectCommitteeRoot(
      final BeaconState state, final UInt64 slot, final Bytes32 committeeRoot) {
    final IntList inclusionListCommittee =
        beaconStateAccessors.getInclusionListCommittee(state, slot);
    final int committeeSize = inclusionListCommittee.size();
    final SszUInt64[] inclusionListCommitteeConverted =
        inclusionListCommittee
            .intStream()
            .mapToObj(index -> SszUInt64.of(UInt64.valueOf(index)))
            .toArray(SszUInt64[]::new);
    final SszList<SszUInt64> inclusionCommitteeSszList =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, committeeSize)
            .of(inclusionListCommitteeConverted);
    final Bytes32 inclusionCommitteeRoot = inclusionCommitteeSszList.hashTreeRoot();
    return committeeRoot.equals(inclusionCommitteeRoot);
  }

  // TODO EIP7805 should we make sure the committee didn't change after checking the root
  // previously?
  public boolean validatorIndexWithinCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 validatorIndex) {
    final IntList inclusionListCommittee =
        beaconStateAccessors.getInclusionListCommittee(state, slot);
    return inclusionListCommittee.contains(validatorIndex.intValue());
  }

  public Int2ObjectMap<UInt64> getValidatorIndexToSlotAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<UInt64> assignmentMap = new Int2ObjectOpenHashMap<>();
    final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);

    for (int slotOffset = 0; slotOffset < slotsPerEpoch; slotOffset++) {
      final UInt64 slot = startSlot.plus(slotOffset);
      final IntList committee = beaconStateAccessors.getInclusionListCommittee(state, slot);
      committee.forEach(validatorIndex -> assignmentMap.put(validatorIndex, slot));
    }
    return assignmentMap;
  }

  /**
   * Returns the slot during the requested epoch in which the validator with index
   * ``validator_index`` is a member of the ILC. Returns None if no assignment is found.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validatorIndex the validator that is calling this function.
   * @return Optional containing the slot if any, empty otherwise
   */
  public Optional<UInt64> getInclusionCommitteeAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(nextEpoch) <= 0,
        "get_inclusion_committee_assignment: Epoch number too high");
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (UInt64 slot = startSlot;
        slot.isLessThan(startSlot.plus(specConfig.getSlotsPerEpoch()));
        slot = slot.plus(UInt64.ONE)) {
      final IntList committee = beaconStateAccessors.getInclusionListCommittee(state, slot);
      if (committee.contains(validatorIndex)) {
        return Optional.of(slot);
      }
    }
    return Optional.empty();
  }
}
