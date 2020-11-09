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

package tech.pegasys.teku.statetransition.validation;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.getAggregatorModulo;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.isAggregator;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_SELECTION_PROOF;
import static tech.pegasys.teku.util.config.Constants.VALID_AGGREGATE_SET_SIZE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class AggregateAttestationValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<AggregatorIndexAndEpoch> receivedAggregatorIndexAndEpochs =
      LimitedSet.create(VALID_AGGREGATE_SET_SIZE);
  private final Set<Bytes32> receivedValidAggregations =
      LimitedSet.create(VALID_AGGREGATE_SET_SIZE);
  private final AttestationValidator attestationValidator;
  private final RecentChainData recentChainData;

  public AggregateAttestationValidator(
      final RecentChainData recentChainData, final AttestationValidator attestationValidator) {
    this.recentChainData = recentChainData;
    this.attestationValidator = attestationValidator;
  }

  public void addSeenAggregate(final ValidateableAttestation attestation) {
    receivedValidAggregations.add(attestation.hash_tree_root());
  }

  public SafeFuture<InternalValidationResult> validate(final ValidateableAttestation attestation) {
    final SignedAggregateAndProof signedAggregate = attestation.getSignedAggregateAndProof();
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Attestation aggregate = aggregateAndProof.getAggregate();

    final UInt64 aggregateSlot = aggregate.getData().getSlot();
    final AggregatorIndexAndEpoch aggregatorIndexAndEpoch =
        new AggregatorIndexAndEpoch(
            aggregateAndProof.getIndex(), compute_epoch_at_slot(aggregateSlot));
    if (receivedAggregatorIndexAndEpochs.contains(aggregatorIndexAndEpoch)) {
      LOG.trace("Ignoring duplicate aggregate");
      return SafeFuture.completedFuture(IGNORE);
    }

    if (receivedValidAggregations.contains(attestation.hash_tree_root())) {
      LOG.trace("Ignoring duplicate aggregate based on hash tree root");
      return SafeFuture.completedFuture(IGNORE);
    }

    return attestationValidator
        .singleOrAggregateAttestationChecks(attestation, OptionalInt.empty())
        .thenCompose(
            aggregateInternalValidationResult -> {
              if (aggregateInternalValidationResult == REJECT
                  || aggregateInternalValidationResult == IGNORE) {
                LOG.trace("Rejecting aggregate because attestation failed validation");
                return SafeFuture.completedFuture(aggregateInternalValidationResult);
              }

              return recentChainData
                  .retrieveBlockState(aggregate.getData().getBeacon_block_root())
                  .thenCompose(
                      maybeState ->
                          maybeState.isEmpty()
                              ? SafeFuture.completedFuture(Optional.empty())
                              : attestationValidator.resolveStateForAttestation(
                                  aggregate, maybeState.get()))
                  .thenApply(
                      maybeState -> {
                        if (maybeState.isEmpty()) {
                          return SAVE_FOR_FUTURE;
                        }

                        final BeaconState state = maybeState.get();

                        final Optional<BLSPublicKey> aggregatorPublicKey =
                            ValidatorsUtil.getValidatorPubKey(state, aggregateAndProof.getIndex());
                        if (aggregatorPublicKey.isEmpty()) {
                          LOG.trace("Rejecting aggregate with invalid index");
                          return REJECT;
                        }

                        if (!isSelectionProofValid(
                            aggregateSlot,
                            state,
                            aggregatorPublicKey.get(),
                            aggregateAndProof.getSelection_proof())) {
                          LOG.trace("Rejecting aggregate with incorrect selection proof");
                          return REJECT;
                        }

                        final List<Integer> beaconCommittee =
                            CommitteeUtil.get_beacon_committee(
                                state, aggregateSlot, aggregate.getData().getIndex());

                        final int aggregatorModulo = getAggregatorModulo(beaconCommittee.size());
                        if (!isAggregator(
                            aggregateAndProof.getSelection_proof(), aggregatorModulo)) {
                          LOG.trace(
                              "Rejecting aggregate because selection proof does not select validator as aggregator");
                          return REJECT;
                        }
                        if (!beaconCommittee.contains(
                            toIntExact(aggregateAndProof.getIndex().longValue()))) {
                          LOG.trace(
                              "Rejecting aggregate because attester is not in committee. Should have been one of {}",
                              beaconCommittee);
                          return REJECT;
                        }

                        if (!isSignatureValid(signedAggregate, state, aggregatorPublicKey.get())) {
                          LOG.trace("Rejecting aggregate with invalid signature");
                          return REJECT;
                        }

                        if (!receivedAggregatorIndexAndEpochs.add(aggregatorIndexAndEpoch)) {
                          LOG.trace("Ignoring duplicate aggregate");
                          return IGNORE;
                        }

                        if (!receivedValidAggregations.add(attestation.hash_tree_root())) {
                          LOG.trace("Ignoring duplicate aggregate based on hash tree root");
                          return IGNORE;
                        }

                        return aggregateInternalValidationResult;
                      });
            });
  }

  private boolean isSignatureValid(
      final SignedAggregateAndProof signedAggregate,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey) {
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Bytes32 domain =
        get_domain(
            Constants.DOMAIN_AGGREGATE_AND_PROOF,
            compute_epoch_at_slot(aggregateAndProof.getAggregate().getData().getSlot()),
            state.getFork(),
            state.getGenesis_validators_root());
    final Bytes signingRoot = compute_signing_root(aggregateAndProof, domain);
    return BLS.verify(aggregatorPublicKey, signingRoot, signedAggregate.getSignature());
  }

  private boolean isSelectionProofValid(
      final UInt64 aggregateSlot,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey,
      final BLSSignature selectionProof) {
    final Bytes32 domain =
        get_domain(
            DOMAIN_SELECTION_PROOF,
            compute_epoch_at_slot(aggregateSlot),
            state.getFork(),
            state.getGenesis_validators_root());
    final Bytes signingRoot = compute_signing_root(aggregateSlot.longValue(), domain);
    return BLS.verify(aggregatorPublicKey, signingRoot, selectionProof);
  }

  private static class AggregatorIndexAndEpoch {
    private final UInt64 aggregatorIndex;
    private final UInt64 epoch;

    private AggregatorIndexAndEpoch(final UInt64 aggregatorIndex, final UInt64 epoch) {
      this.aggregatorIndex = aggregatorIndex;
      this.epoch = epoch;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AggregatorIndexAndEpoch that = (AggregatorIndexAndEpoch) o;
      return Objects.equals(aggregatorIndex, that.aggregatorIndex)
          && Objects.equals(epoch, that.epoch);
    }

    @Override
    public int hashCode() {
      return Objects.hash(aggregatorIndex, epoch);
    }
  }
}
