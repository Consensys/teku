/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import com.google.common.base.Preconditions;
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
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Committee;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.util.config.Constants;

public class AttestationGenerator {
  private final List<BLSKeyPair> validatorKeys;
  private final BLSKeyPair randomKeyPair = BLSKeyPair.random(12345);

  public AttestationGenerator(final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public static int getSingleAttesterIndex(Attestation attestation) {
    return attestation.getAggregation_bits().streamAllSetBits().findFirst().orElse(-1);
  }

  public static AttestationData diffSlotAttestationData(UInt64 slot, AttestationData data) {
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
   * tech.pegasys.teku.datastructures.operations.AttestationData} and aggregates attestations in
   * every group to a single {@link Attestation}
   *
   * @return a list of aggregated {@link Attestation}s with distinct {@link
   *     tech.pegasys.teku.datastructures.operations.AttestationData}
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

  public Attestation validAttestation(final StateAndBlockSummary blockAndState) {
    return validAttestation(blockAndState, blockAndState.getSlot());
  }

  public Attestation validAttestation(final StateAndBlockSummary blockAndState, final UInt64 slot) {
    return createAttestation(blockAndState, true, slot);
  }

  public Attestation attestationWithInvalidSignature(final StateAndBlockSummary blockAndState) {
    return createAttestation(blockAndState, false, blockAndState.getSlot());
  }

  private Attestation createAttestation(
      final StateAndBlockSummary blockAndState,
      final boolean withValidSignature,
      final UInt64 slot) {
    UInt64 assignedSlot = slot;

    Optional<Attestation> attestation = Optional.empty();
    while (attestation.isEmpty()) {
      Stream<Attestation> attestations =
          withValidSignature
              ? streamAttestations(blockAndState, assignedSlot)
              : streamInvalidAttestations(blockAndState, assignedSlot);
      attestation = attestations.findFirst();
      assignedSlot = assignedSlot.plus(UInt64.ONE);
    }

    return attestation.orElseThrow();
  }

  public List<Attestation> getAttestationsForSlot(final StateAndBlockSummary blockAndState) {
    return getAttestationsForSlot(blockAndState, blockAndState.getSlot());
  }

  public List<Attestation> getAttestationsForSlot(
      final StateAndBlockSummary blockAndState, final UInt64 slot) {

    return streamAttestations(blockAndState, slot).collect(Collectors.toList());
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
      final StateAndBlockSummary headBlockAndState, final UInt64 assignedSlot) {
    return AttestationIterator.create(headBlockAndState, assignedSlot, validatorKeys).toStream();
  }

  /**
   * Streams invalid attestations for validators assigned to attest at {@code assignedSlot}, using
   * the given {@code headBlockAndState} as the calculated chain head.
   *
   * @param headBlockAndState The chain head to attest to
   * @param assignedSlot The assigned slot for which to produce attestations
   * @return A stream of invalid attestations produced at the assigned slot
   */
  private Stream<Attestation> streamInvalidAttestations(
      final StateAndBlockSummary headBlockAndState, final UInt64 assignedSlot) {
    return AttestationIterator.createWithInvalidSignatures(
            headBlockAndState, assignedSlot, validatorKeys, randomKeyPair)
        .toStream();
  }

  /**
   * Iterates through valid attestations with the supplied head block, produced at the given
   * assigned slot.
   */
  private static class AttestationIterator implements Iterator<Attestation> {
    // The latest block being attested to
    private final BeaconBlockSummary headBlock;
    // The latest state processed through to the current slot
    private final BeaconState headState;
    // The assigned slot to generate attestations for
    private final UInt64 assignedSlot;
    // The epoch containing the assigned slot
    private final UInt64 assignedSlotEpoch;
    // Validator keys
    private final List<BLSKeyPair> validatorKeys;
    private final Function<Integer, BLSKeyPair> validatorKeySupplier;

    private Optional<Attestation> nextAttestation = Optional.empty();
    private int currentValidatorIndex = 0;

    private AttestationIterator(
        final StateAndBlockSummary headBlockAndState,
        final UInt64 assignedSlot,
        final List<BLSKeyPair> validatorKeys,
        final Function<Integer, BLSKeyPair> validatorKeySupplier) {
      this.headBlock = headBlockAndState;
      this.headState = generateHeadState(headBlockAndState.getState(), assignedSlot);
      this.validatorKeys = validatorKeys;
      this.assignedSlot = assignedSlot;
      this.assignedSlotEpoch = compute_epoch_at_slot(assignedSlot);
      this.validatorKeySupplier = validatorKeySupplier;
      generateNextAttestation();
    }

    private BeaconState generateHeadState(final BeaconState state, final UInt64 slot) {
      if (state.getSlot().equals(slot)) {
        return state;
      }

      StateTransition stateTransition = new StateTransition();
      try {
        return stateTransition.process_slots(state, slot);
      } catch (EpochProcessingException | SlotProcessingException e) {
        throw new IllegalStateException(e);
      }
    }

    public static AttestationIterator create(
        final StateAndBlockSummary headBlockAndState,
        final UInt64 assignedSlot,
        final List<BLSKeyPair> validatorKeys) {
      return new AttestationIterator(
          headBlockAndState, assignedSlot, validatorKeys, validatorKeys::get);
    }

    public static AttestationIterator createWithInvalidSignatures(
        final StateAndBlockSummary headBlockAndState,
        final UInt64 assignedSlot,
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
        UInt64 committeeIndex = assignment.getCommitteeIndex();
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
          new LocalSigner(attesterKeyPair, SYNC_RUNNER)
              .signAttestationData(attestationData, state.getForkInfo())
              .join();
      return new Attestation(aggregationBitfield, attestationData, signature);
    }
  }
}
