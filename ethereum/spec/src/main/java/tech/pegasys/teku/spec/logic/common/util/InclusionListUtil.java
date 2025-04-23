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
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.BeaconStateAccessorsEip7805;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;

public class InclusionListUtil {

  protected final SchemaDefinitionsEip7805 schemaDefinitionsEip7805;
  protected final BeaconStateAccessorsEip7805 beaconStateAccessorsEip7805;
  protected final MiscHelpers miscHelpers;
  protected final SpecConfigEip7805 specConfigEip7805;

  public InclusionListUtil(
      final SpecConfigEip7805 specConfigEip7805,
      final SchemaDefinitionsEip7805 schemaDefinitionsEip7805,
      final BeaconStateAccessorsEip7805 beaconStateAccessorsEip7805,
      final MiscHelpers miscHelpers) {
    this.specConfigEip7805 = specConfigEip7805;
    this.schemaDefinitionsEip7805 = schemaDefinitionsEip7805;
    this.beaconStateAccessorsEip7805 = beaconStateAccessorsEip7805;
    this.miscHelpers = miscHelpers;
  }

  public boolean isInclusionListForCurrentOrPreviousSlot(
      final UInt64 inclusionListSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 currentSlot =
        miscHelpers.computeSlotAtTime(genesisTime, millisToSeconds(currentTimeMillis));
    return inclusionListSlot.equals(currentSlot) || inclusionListSlot.equals(currentSlot.minus(1));
  }

  public boolean isInclusionListWithinDeadline(
      final UInt64 inclusionListSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 currentSlot =
        miscHelpers.computeSlotAtTime(genesisTime, millisToSeconds(currentTimeMillis));
    final UInt64 millisInCurrentSlot =
        getMillisIntoSlot(genesisTime, currentTimeMillis, inclusionListSlot);
    return inclusionListSlot.equals(currentSlot)
        || (inclusionListSlot.equals(currentSlot.minus(1))
            && millisToSeconds(millisInCurrentSlot)
                .isLessThanOrEqualTo(specConfigEip7805.getAttestationDeadLine()));
  }

  /** Check if ``signed_inclusion_list`` has a valid signature. */
  // TODO EIP7805 return InclusionListProcessingResult instead of Boolean
  public SafeFuture<Boolean> isValidInclusionListSignature(
      final Fork fork,
      final BeaconState state,
      final InclusionList inclusionList,
      final BLSSignature signature,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final UInt64 index = inclusionList.getValidatorIndex();
    final BLSPublicKey pubkey = state.getValidators().get(index.intValue()).getPublicKey();
    final Bytes32 domain =
        beaconStateAccessorsEip7805.getDomain(
            Domain.DOMAIN_INCLUSION_LIST_COMMITTEE,
            miscHelpers.computeEpochAtSlot(inclusionList.getSlot()),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(inclusionList, domain);
    return signatureVerifier.verify(pubkey, signingRoot, signature);
  }

  public IntList getInclusionListCommittee(final BeaconState state, final UInt64 slot) {
    return beaconStateAccessorsEip7805.getInclusionListCommittee(state, slot);
  }

  public boolean hasCorrectCommitteeRoot(
      final BeaconState state, final UInt64 slot, final Bytes32 committeeRoot) {
    final Bytes32 inclusionCommitteeRoot = getInclusionListCommitteeRoot(state, slot);
    return committeeRoot.equals(inclusionCommitteeRoot);
  }

  public Bytes32 getInclusionListCommitteeRoot(final BeaconState state, final UInt64 slot) {
    final List<UInt64> inclusionListCommittee =
        getInclusionListCommittee(state, slot).intStream().mapToObj(UInt64::valueOf).toList();
    final SszVector<SszUInt64> sszInclusionListCommittee =
        schemaDefinitionsEip7805
            .getInclusionListCommitteeRootSchema()
            .createFromElements(inclusionListCommittee.stream().map(SszUInt64::of).toList());
    return sszInclusionListCommittee.hashTreeRoot();
  }

  // TODO EIP7805 should we make sure the committee didn't change after checking the root
  // previously?
  public boolean validatorIndexWithinCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 validatorIndex) {
    final IntList inclusionListCommittee =
        beaconStateAccessorsEip7805.getInclusionListCommittee(state, slot);
    return inclusionListCommittee.contains(validatorIndex.intValue());
  }

  public Int2ObjectMap<UInt64> getValidatorIndexToSlotAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<UInt64> assignmentMap = new Int2ObjectOpenHashMap<>();
    final int slotsPerEpoch = specConfigEip7805.getSlotsPerEpoch();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);

    for (int slotOffset = 0; slotOffset < slotsPerEpoch; slotOffset++) {
      final UInt64 slot = startSlot.plus(slotOffset);
      final IntList committee = beaconStateAccessorsEip7805.getInclusionListCommittee(state, slot);
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
  public Optional<UInt64> getInclusionListCommitteeAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    final UInt64 nextEpoch = beaconStateAccessorsEip7805.getCurrentEpoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(nextEpoch) <= 0,
        "get_inclusion_committee_assignment: Epoch number too high");
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (UInt64 slot = startSlot;
        slot.isLessThan(startSlot.plus(specConfigEip7805.getSlotsPerEpoch()));
        slot = slot.plus(UInt64.ONE)) {
      final IntList committee = beaconStateAccessorsEip7805.getInclusionListCommittee(state, slot);
      if (committee.contains(validatorIndex)) {
        return Optional.of(slot);
      }
    }
    return Optional.empty();
  }

  private UInt64 getMillisIntoSlot(
      final UInt64 genesisTime, final UInt64 currentTimeMillis, final UInt64 slot) {
    final UInt64 timeAtSlotSeconds = miscHelpers.computeTimeAtSlot(genesisTime, slot);
    return currentTimeMillis.min(secondsToMillis(timeAtSlotSeconds));
  }
}
