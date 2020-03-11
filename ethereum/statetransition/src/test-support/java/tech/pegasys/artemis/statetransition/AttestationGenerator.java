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

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.util.CommitteeAssignmentUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

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

  /**
   * Groups passed attestations by their {@link
   * tech.pegasys.artemis.datastructures.operations.AttestationData} and aggregates attestations in
   * every group to a single {@link Attestation}
   *
   * @return a list of aggregated {@link Attestation}s with distinct {@link
   *     tech.pegasys.artemis.datastructures.operations.AttestationData}
   */
  public static List<Attestation> groupAndAggregateAttestations(List<Attestation> srcAttestations) {
    Collection<List<Attestation>> groupedAtt =
        srcAttestations.stream().collect(Collectors.groupingBy(Attestation::getData)).values();
    return groupedAtt.stream()
        .map(AttestationGenerator::aggregateAttestations)
        .collect(Collectors.toList());
  }

  /**
   * Aggregates passed attestations
   *
   * @param srcAttestations attestations which should have the same {@link Attestation#getData()}
   */
  public static Attestation aggregateAttestations(List<Attestation> srcAttestations) {
    Preconditions.checkArgument(!srcAttestations.isEmpty(), "Expected at least one attestation");

    int targetBitlistSize =
        srcAttestations.stream()
            .mapToInt(a -> a.getAggregation_bits().getCurrentSize())
            .max()
            .getAsInt();
    Bitlist targetBitlist = new Bitlist(targetBitlistSize, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    srcAttestations.forEach(a -> targetBitlist.setAllBits(a.getAggregation_bits()));
    BLSSignature targetSig =
        BLSAggregate.bls_aggregate_signatures(
            srcAttestations.stream()
                .map(Attestation::getAggregate_signature)
                .collect(Collectors.toList()));

    return new Attestation(targetBitlist, srcAttestations.get(0).getData(), targetSig);
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

  public Attestation attestationWithInvalidSignature(
      final BeaconBlock block, final BeaconState state)
      throws EpochProcessingException, SlotProcessingException {
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

  private BeaconState processStateToSlot(BeaconState preState, UnsignedLong slot)
      throws EpochProcessingException, SlotProcessingException {
    final StateTransition stateTransition = new StateTransition();
    final MutableBeaconState postState = preState.createWritableCopy();

    stateTransition.process_slots(postState, slot);
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
