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

package tech.pegasys.artemis.core;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.core.signatures.LocalMessageSignerService;
import tech.pegasys.artemis.core.signatures.Signer;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.config.Constants;

public class AttestationGenerator {
  private final List<BLSKeyPair> validatorKeys;
  private final BLSKeyPair randomKeyPair = BLSKeyPair.random(12345);

  public AttestationGenerator(final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public static int getSingleAttesterIndex(Attestation attestation) {
    return attestation.getAggregation_bits().streamAllSetBits().findFirst().orElse(-1);
  }

  public static AttestationData diffSlotAttestationData(UnsignedLong slot, AttestationData data) {
    return new AttestationData(
        slot, data.getIndex(), data.getBeacon_block_root(), data.getSource(), data.getTarget());
  }

  public static Attestation withNewSingleAttesterBit(Attestation oldAttestation) {
    Attestation attestation = new Attestation(oldAttestation);
    Bitlist newBitlist =
        new Bitlist(
            attestation.getAggregation_bits().getCurrentSize(),
            attestation.getAggregation_bits().getMaxSize());
    List<Integer> unsetBits = new ArrayList<>();
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (!attestation.getAggregation_bits().getBit(i)) {
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
        BLS.aggregate(
            srcAttestations.stream()
                .map(Attestation::getAggregate_signature)
                .collect(Collectors.toList()));

    return new Attestation(targetBitlist, srcAttestations.get(0).getData(), targetSig);
  }

  public Attestation validAttestation(final BeaconBlockAndState blockAndState)
      throws EpochProcessingException, SlotProcessingException {
    return createAttestation(blockAndState, true);
  }

  public Attestation attestationWithInvalidSignature(final BeaconBlockAndState blockAndState)
      throws EpochProcessingException, SlotProcessingException {
    return createAttestation(blockAndState, false);
  }

  private Attestation createAttestation(
      final BeaconBlockAndState blockAndState, final boolean withValidSignature) {

    Optional<Attestation> attestation = Optional.empty();
    UnsignedLong assignedSlot = blockAndState.getBlock().getSlot();
    while (attestation.isEmpty()) {
      Stream<Attestation> attestations =
          withValidSignature
              ? streamAttestations(blockAndState, assignedSlot)
              : streamInvalidAttestations(blockAndState, assignedSlot);
      attestation = attestations.findFirst();
      assignedSlot = assignedSlot.plus(UnsignedLong.ONE);
    }

    return attestation.orElseThrow();
  }

  public List<Attestation> getAttestationsForSlot(
      final BeaconState state, final BeaconBlock block, final UnsignedLong slot) {

    return streamAttestations(new BeaconBlockAndState(block, state), slot)
        .collect(Collectors.toList());
  }

  /**
   * Streams attestations for validators assigned to attest at {@code assignedSlot}, using the given
   * {@code headBlockAndState} as the calculated chain head.
   *
   * @param headBlockAndState The chain head to attest to
   * @param assignedSlot The assigned slot for which to produce attestations
   * @return A stream of valid attestations to produce at the assigned slot
   */
  public Stream<Attestation> streamAttestations(
      final BeaconBlockAndState headBlockAndState, final UnsignedLong assignedSlot) {
    return AttestationIterator.create(headBlockAndState, assignedSlot, validatorKeys).toStream();
  };

  /**
   * Streams invalid attestations for validators assigned to attest at {@code assignedSlot}, using
   * the given {@code headBlockAndState} as the calculated chain head.
   *
   * @param headBlockAndState The chain head to attest to
   * @param assignedSlot The assigned slot for which to produce attestations
   * @return A stream of invalid attestations produced at the assigned slot
   */
  private Stream<Attestation> streamInvalidAttestations(
      final BeaconBlockAndState headBlockAndState, final UnsignedLong assignedSlot) {
    return AttestationIterator.createWithInvalidSignatures(
            headBlockAndState, assignedSlot, validatorKeys, randomKeyPair)
        .toStream();
  };

  /**
   * Iterates through valid attestations with the supplied head block, produced at the given
   * assigned slot.
   */
  private static class AttestationIterator implements Iterator<Attestation> {
    // The head block to attest to with its corresponding state
    private final BeaconBlockAndState headBlockAndState;
    // The assigned slot to generate attestations for
    private final UnsignedLong assignedSlot;
    // The epoch containing the assigned slot
    private final UnsignedLong assignedSlotEpoch;
    // Validator keys
    private final List<BLSKeyPair> validatorKeys;
    private final Function<Integer, BLSKeyPair> validatorKeySupplier;

    private Optional<Attestation> nextAttestation = Optional.empty();
    private int currentValidatorIndex = 0;

    private AttestationIterator(
        final BeaconBlockAndState headBlockAndState,
        final UnsignedLong assignedSlot,
        final List<BLSKeyPair> validatorKeys,
        final Function<Integer, BLSKeyPair> validatorKeySupplier) {
      this.headBlockAndState = headBlockAndState;
      this.validatorKeys = validatorKeys;
      this.assignedSlot = assignedSlot;
      this.assignedSlotEpoch = compute_epoch_at_slot(assignedSlot);
      this.validatorKeySupplier = validatorKeySupplier;
      generateNextAttestation();
    }

    public static AttestationIterator create(
        final BeaconBlockAndState headBlockAndState,
        final UnsignedLong assignedSlot,
        final List<BLSKeyPair> validatorKeys) {
      return new AttestationIterator(
          headBlockAndState, assignedSlot, validatorKeys, validatorKeys::get);
    }

    public static AttestationIterator createWithInvalidSignatures(
        final BeaconBlockAndState headBlockAndState,
        final UnsignedLong assignedSlot,
        final List<BLSKeyPair> validatorKeys,
        final BLSKeyPair invalidKeyPair) {
      return new AttestationIterator(
          headBlockAndState, assignedSlot, validatorKeys, __ -> invalidKeyPair);
    }

    public Stream<Attestation> toStream() {
      final Spliterator<Attestation> split =
          Spliterators.spliteratorUnknownSize(
              this, Spliterator.IMMUTABLE | Spliterator.DISTINCT | Spliterator.NONNULL);

      return StreamSupport.stream(split, false);
    }

    @Override
    public boolean hasNext() {
      return nextAttestation.isPresent();
    }

    @Override
    public Attestation next() {
      if (nextAttestation.isEmpty()) {
        throw new NoSuchElementException();
      }
      final Attestation attestation = nextAttestation.get();
      generateNextAttestation();
      return attestation;
    }

    private void generateNextAttestation() {
      nextAttestation = Optional.empty();

      final BeaconState headState = headBlockAndState.getState();
      final BeaconBlock headBlock = headBlockAndState.getBlock();
      int lastProcessedValidatorIndex = currentValidatorIndex;
      for (int validatorIndex = currentValidatorIndex;
          validatorIndex < validatorKeys.size();
          validatorIndex++) {
        lastProcessedValidatorIndex = validatorIndex;
        final Optional<CommitteeAssignment> maybeAssignment =
            CommitteeAssignmentUtil.get_committee_assignment(
                headState, assignedSlotEpoch, validatorIndex);

        if (maybeAssignment.isEmpty()) {
          continue;
        }

        CommitteeAssignment assignment = maybeAssignment.get();
        if (!assignment.getSlot().equals(assignedSlot)) {
          continue;
        }

        List<Integer> committeeIndices = assignment.getCommittee();
        UnsignedLong committeeIndex = assignment.getCommitteeIndex();
        Committee committee = new Committee(committeeIndex, committeeIndices);
        int indexIntoCommittee = committeeIndices.indexOf(validatorIndex);
        AttestationData genericAttestationData =
            AttestationUtil.getGenericAttestationData(
                assignedSlot, headState, headBlock, committeeIndex);
        final BLSKeyPair validatorKeyPair = validatorKeySupplier.apply(validatorIndex);
        nextAttestation =
            Optional.of(
                createAttestation(
                    headState,
                    validatorKeyPair,
                    indexIntoCommittee,
                    committee,
                    genericAttestationData));
        break;
      }

      currentValidatorIndex = lastProcessedValidatorIndex + 1;
    }

    private Attestation createAttestation(
        BeaconState state,
        BLSKeyPair attesterKeyPair,
        int indexIntoCommittee,
        Committee committee,
        AttestationData attestationData) {
      int committeSize = committee.getCommitteeSize();
      Bitlist aggregationBitfield =
          AttestationUtil.getAggregationBits(committeSize, indexIntoCommittee);

      BLSSignature signature =
          new Signer(new LocalMessageSignerService(attesterKeyPair))
              .signAttestationData(attestationData, state.getFork())
              .join();
      return new Attestation(aggregationBitfield, attestationData, signature);
    }
  }
}
