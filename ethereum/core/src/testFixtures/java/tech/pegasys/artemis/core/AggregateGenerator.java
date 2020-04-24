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

package tech.pegasys.artemis.core;

import static org.assertj.core.util.Preconditions.checkNotNull;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.signatures.LocalMessageSignerService;
import tech.pegasys.artemis.core.signatures.Signer;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;

public class AggregateGenerator {
  private final AttestationGenerator attestationGenerator;
  private final List<BLSKeyPair> validatorKeys;

  public AggregateGenerator(final List<BLSKeyPair> validatorKeys) {
    attestationGenerator = new AttestationGenerator(validatorKeys);
    this.validatorKeys = validatorKeys;
  }

  public AttestationGenerator getAttestationGenerator() {
    return attestationGenerator;
  }

  public Builder generator() {
    return new Builder();
  }

  public SignedAggregateAndProof validAggregateAndProof(final BeaconBlockAndState blockAndState) {
    return generator().blockAndState(blockAndState).generate();
  }

  public SignedAggregateAndProof validAggregateAndProof(
      final BeaconBlockAndState blockAndState, final UnsignedLong slot) {
    return generator().blockAndState(blockAndState).slot(slot).generate();
  }

  private Signer getSignerForValidatorIndex(final int validatorIndex) {
    return new Signer(new LocalMessageSignerService(validatorKeys.get(validatorIndex)));
  }

  public class Builder {
    private BeaconBlockAndState blockAndState;
    private Optional<UnsignedLong> aggregatorIndex = Optional.empty();
    private Optional<UnsignedLong> slot = Optional.empty();
    private Optional<Attestation> aggregate = Optional.empty();
    private Optional<BLSSignature> selectionProof = Optional.empty();
    private Optional<UnsignedLong> committeeIndex = Optional.empty();

    public Builder blockAndState(final BeaconBlockAndState blockAndState) {
      this.blockAndState = blockAndState;
      return this;
    }

    public Builder slot(final UnsignedLong slot) {
      this.slot = Optional.of(slot);
      return this;
    }

    public Builder aggregatorIndex(final UnsignedLong aggregatorIndex) {
      this.aggregatorIndex = Optional.of(aggregatorIndex);
      return this;
    }

    public Builder aggregate(final Attestation aggregate) {
      this.aggregate = Optional.of(aggregate);
      return this;
    }

    public Builder committeeIndex(final UnsignedLong committeeIndex) {
      this.committeeIndex = Optional.of(committeeIndex);
      return this;
    }

    public Builder selectionProof(final BLSSignature selectionProof) {
      this.selectionProof = Optional.of(selectionProof);
      return this;
    }

    public SignedAggregateAndProof generate() {
      checkNotNull(blockAndState, "Missing block and state");
      final UnsignedLong slot = this.slot.orElseGet(blockAndState::getSlot);
      final Attestation aggregate = getAggregate(slot);
      final BeaconState state = blockAndState.getState();

      UnsignedLong aggregatorIndex = null;
      BLSSignature validSelectionProof = null;
      if (this.aggregatorIndex.isPresent()) {
        aggregatorIndex = this.aggregatorIndex.get();
        validSelectionProof =
            createSelectionProof(this.aggregatorIndex.get().intValue(), state, slot);
      } else {
        final List<Integer> beaconCommittee =
            CommitteeUtil.get_beacon_committee(
                state, aggregate.getData().getSlot(), aggregate.getData().getIndex());
        for (int validatorIndex : beaconCommittee) {
          final Optional<BLSSignature> maybeSelectionProof =
              validSelectionProof(validatorIndex, state, aggregate);
          if (maybeSelectionProof.isPresent()) {
            aggregatorIndex = UnsignedLong.valueOf(validatorIndex);
            validSelectionProof = maybeSelectionProof.get();
            break;
          }
        }

        if (aggregatorIndex == null) {
          throw new NoSuchElementException("No valid aggregate possible");
        }
      }

      final BLSSignature selectionProof = this.selectionProof.orElse(validSelectionProof);
      final AggregateAndProof aggregateAndProof =
          new AggregateAndProof(aggregatorIndex, aggregate, selectionProof);
      return createSignedAggregateAndProof(aggregatorIndex, aggregateAndProof, state);
    }

    private Attestation getAggregate(final UnsignedLong slot) {
      return this.aggregate.orElseGet(
          () -> {
            if (committeeIndex.isPresent()) {
              return attestationGenerator
                  .streamAttestations(blockAndState, slot)
                  .filter(
                      attestation -> attestation.getData().getIndex().equals(committeeIndex.get()))
                  .findFirst()
                  .orElseThrow();
            }
            return attestationGenerator.validAttestation(blockAndState, slot);
          });
    }

    private Optional<BLSSignature> validSelectionProof(
        final int validatorIndex, final BeaconState state, final Attestation attestation) {
      final UnsignedLong slot = attestation.getData().getSlot();
      final UnsignedLong committeeIndex = attestation.getData().getIndex();
      final List<Integer> beaconCommittee =
          CommitteeUtil.get_beacon_committee(state, slot, committeeIndex);
      final int aggregatorModulo = CommitteeUtil.getAggregatorModulo(beaconCommittee.size());
      final BLSSignature selectionProof = createSelectionProof(validatorIndex, state, slot);
      if (CommitteeUtil.isAggregator(selectionProof, aggregatorModulo)) {
        return Optional.of(selectionProof);
      }
      return Optional.empty();
    }

    private BLSSignature createSelectionProof(
        final int validatorIndex, final BeaconState state, final UnsignedLong slot) {
      return getSignerForValidatorIndex(validatorIndex)
          .signAggregationSlot(slot, state.getForkInfo())
          .join();
    }

    private SignedAggregateAndProof createSignedAggregateAndProof(
        final UnsignedLong aggregatorIndex,
        final AggregateAndProof aggregateAndProof,
        final BeaconState state) {
      final BLSSignature aggregateSignature =
          getSignerForValidatorIndex(aggregatorIndex.intValue())
              .signAggregateAndProof(aggregateAndProof, state.getForkInfo())
              .join();
      return new SignedAggregateAndProof(aggregateAndProof, aggregateSignature);
    }
  }
}
