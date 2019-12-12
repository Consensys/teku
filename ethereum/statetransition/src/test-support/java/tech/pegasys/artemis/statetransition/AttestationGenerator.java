/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.util.CommitteeAssignmentUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class AttestationGenerator {
  private final List<BLSKeyPair> validatorKeys;
  private final BLSKeyPair randomKeyPair = BLSKeyPair.random();

  public AttestationGenerator(final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public static int getSingleAttesterIndex(Attestation attestation) {
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (attestation.getAggregation_bits().getBit(i) == 1) return i;
    }
    return -1;
  }

  public static AttestationData diffSlotAttestationData(UnsignedLong slot, AttestationData data) {
    return new AttestationData(
        slot, data.getIndex(), data.getBeacon_block_root(), data.getSource(), data.getTarget());
  }

  public static Attestation aggregateAttestation(int numAttesters) {
    Attestation attestation = DataStructureUtil.randomAttestation(1);
    withNewAttesterBits(attestation, numAttesters);
    return attestation;
  }

  public static Attestation withNewAttesterBits(Attestation oldAttestation, int numNewAttesters) {
    Attestation attestation = new Attestation(oldAttestation);
    Bitlist newBitlist = attestation.getAggregation_bits().copy();
    List<Integer> unsetBits = new ArrayList<>();
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (newBitlist.getBit(i) == 0) {
        unsetBits.add(i);
      }
    }

    Collections.shuffle(unsetBits);
    for (int i = 0; i < numNewAttesters; i++) {
      newBitlist.setBit(unsetBits.get(i));
    }

    attestation.setAggregation_bits(newBitlist);
    return attestation;
  }

  public static Attestation withNewSingleAttesterBit(Attestation oldAttestation) {
    Attestation attestation = new Attestation(oldAttestation);
    Bitlist newBitlist =
        new Bitlist(
            attestation.getAggregation_bits().getCurrentSize(),
            attestation.getAggregation_bits().getMaxSize());
    List<Integer> unsetBits = new ArrayList<>();
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (attestation.getAggregation_bits().getBit(i) == 0) {
        unsetBits.add(i);
      }
    }

    Collections.shuffle(unsetBits);
    newBitlist.setBit(unsetBits.get(0));

    attestation.setAggregation_bits(newBitlist);
    return attestation;
  }

  public Attestation validAttestation(final ChainStorageClient storageClient)
      throws EpochProcessingException, SlotProcessingException {
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    BeaconBlock block = storageClient.getStore().getBlock(bestBlockRoot);
    BeaconState state = storageClient.getStore().getBlockState(bestBlockRoot);
    return createAttestation(block, state, true);
  }

  public Attestation attestationWithInvalidSignature(final ChainStorageClient storageClient)
      throws EpochProcessingException, SlotProcessingException {
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    BeaconBlock block = storageClient.getStore().getBlock(bestBlockRoot);
    BeaconState state = storageClient.getStore().getBlockState(bestBlockRoot);
    return createAttestation(block, state, false);
  }

  private Attestation createAttestation(
      final BeaconBlock block, final BeaconState state, final boolean withValidSignature)
      throws EpochProcessingException, SlotProcessingException {
    final UnsignedLong epoch = compute_epoch_at_slot(state.getSlot());
    Optional<CommitteeAssignment> committeeAssignment = Optional.empty();
    Optional<UnsignedLong> slot = Optional.empty();
    int validatorIndex;
    for (validatorIndex = 0; validatorIndex < validatorKeys.size(); validatorIndex++) {
      final Optional<CommitteeAssignment> maybeAssignment =
          CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex);
      if (maybeAssignment.isPresent()) {
        CommitteeAssignment assignment = maybeAssignment.get();
        slot = Optional.of(assignment.getSlot());
        committeeAssignment = Optional.of(assignment);
        break;
      }
    }
    if (committeeAssignment.isEmpty()) {
      throw new IllegalStateException("Unable to find committee assignment among validators");
    }

    final BeaconState postState = processStateToSlot(state, slot.get());

    List<Integer> committeeIndices = committeeAssignment.get().getCommittee();
    UnsignedLong committeeIndex = committeeAssignment.get().getCommitteeIndex();
    Committee committee = new Committee(committeeIndex, committeeIndices);
    int indexIntoCommittee = committeeIndices.indexOf(validatorIndex);
    AttestationData genericAttestationData =
        AttestationUtil.getGenericAttestationData(postState, block);

    final BLSKeyPair validatorKeyPair =
        withValidSignature ? validatorKeys.get(validatorIndex) : randomKeyPair;
    return createAttestation(
        state, validatorKeyPair, indexIntoCommittee, committee, genericAttestationData);
  }

  public List<Attestation> getAttestationsForSlot(
      final BeaconState state, final BeaconBlock block, final UnsignedLong slot) {

    final UnsignedLong epoch = compute_epoch_at_slot(slot);
    List<Attestation> attestations = new ArrayList<>();

    int validatorIndex;
    for (validatorIndex = 0; validatorIndex < validatorKeys.size(); validatorIndex++) {

      final Optional<CommitteeAssignment> maybeAssignment =
          CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex);

      if (maybeAssignment.isEmpty()) {
        continue;
      }

      CommitteeAssignment assignment = maybeAssignment.get();
      if (!assignment.getSlot().equals(slot)) {
        continue;
      }

      List<Integer> committeeIndices = assignment.getCommittee();
      UnsignedLong committeeIndex = assignment.getCommitteeIndex();
      Committee committee = new Committee(committeeIndex, committeeIndices);
      int indexIntoCommittee = committeeIndices.indexOf(validatorIndex);
      AttestationData genericAttestationData =
          AttestationUtil.getGenericAttestationData(state, block);
      final BLSKeyPair validatorKeyPair = validatorKeys.get(validatorIndex);
      attestations.add(
          createAttestation(
              state, validatorKeyPair, indexIntoCommittee, committee, genericAttestationData));
    }

    return attestations;
  }

  private BeaconStateWithCache processStateToSlot(BeaconState preState, UnsignedLong slot)
      throws EpochProcessingException, SlotProcessingException {
    final StateTransition stateTransition = new StateTransition(false);
    final BeaconStateWithCache postState = BeaconStateWithCache.fromBeaconState(preState);

    stateTransition.process_slots(postState, slot, false);
    return postState;
  }

  private Attestation createAttestation(
      BeaconState state,
      BLSKeyPair attesterKeyPair,
      int indexIntoCommittee,
      Committee committee,
      AttestationData genericAttestationData) {
    int commmitteSize = committee.getCommitteeSize();
    Bitlist aggregationBitfield =
        AttestationUtil.getAggregationBits(commmitteSize, indexIntoCommittee);
    AttestationData attestationData = genericAttestationData.withIndex(committee.getIndex());
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain =
        get_domain(state, DOMAIN_BEACON_ATTESTER, attestationData.getTarget().getEpoch());

    BLSSignature signature = BLSSignature.sign(attesterKeyPair, attestationMessage, domain);
    return new Attestation(aggregationBitfield, attestationData, signature);
  }
}
