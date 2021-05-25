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
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.get_domain;
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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
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
  private final Spec spec;

  public AggregateAttestationValidator(
      final RecentChainData recentChainData,
      final AttestationValidator attestationValidator,
      final Spec spec) {
    this.recentChainData = recentChainData;
    this.attestationValidator = attestationValidator;
    this.spec = spec;
  }

  public void addSeenAggregate(final ValidateableAttestation attestation) {
    receivedValidAggregations.add(attestation.hash_tree_root());
  }

  public SafeFuture<InternalValidationResult> validate(final ValidateableAttestation attestation) {
    final SignedAggregateAndProof signedAggregate = attestation.getSignedAggregateAndProof();
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Attestation aggregate = aggregateAndProof.getAggregate();
    final UInt64 aggregateSlot = aggregate.getData().getSlot();
    final SpecVersion specVersion = spec.atSlot(aggregateSlot);

    final AggregatorIndexAndEpoch aggregatorIndexAndEpoch =
        new AggregatorIndexAndEpoch(
            aggregateAndProof.getIndex(), compute_epoch_at_slot(aggregateSlot));
    if (receivedAggregatorIndexAndEpochs.contains(aggregatorIndexAndEpoch)) {
      LOG.trace("Ignoring duplicate aggregate");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    if (receivedValidAggregations.contains(attestation.hash_tree_root())) {
      LOG.trace("Ignoring duplicate aggregate based on hash tree root");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    final BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    return attestationValidator
        .singleOrAggregateAttestationChecks(signatureVerifier, attestation, OptionalInt.empty())
        .thenCompose(
            aggregateInternalValidationResult -> {
              if (aggregateInternalValidationResult.isNotProcessable()) {
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
                          return InternalValidationResult.SAVE_FOR_FUTURE;
                        }

                        final BeaconState state = maybeState.get();

                        final Optional<BLSPublicKey> aggregatorPublicKey =
                            ValidatorsUtil.getValidatorPubKey(state, aggregateAndProof.getIndex());
                        if (aggregatorPublicKey.isEmpty()) {
                          LOG.trace("Rejecting aggregate with invalid index");
                          return InternalValidationResult.REJECT;
                        }

                        if (!isSelectionProofValid(
                            signatureVerifier,
                            aggregateSlot,
                            state,
                            aggregatorPublicKey.get(),
                            aggregateAndProof.getSelection_proof())) {
                          LOG.trace("Rejecting aggregate with incorrect selection proof");
                          return InternalValidationResult.REJECT;
                        }

                        final List<Integer> beaconCommittee =
                            spec.getBeaconCommittee(
                                state, aggregateSlot, aggregate.getData().getIndex());

                        final int aggregatorModulo =
                            specVersion
                                .getValidatorsUtil()
                                .getAggregatorModulo(beaconCommittee.size());
                        if (!specVersion
                            .getValidatorsUtil()
                            .isAggregator(
                                aggregateAndProof.getSelection_proof(), aggregatorModulo)) {
                          LOG.trace(
                              "Rejecting aggregate because selection proof does not select validator as aggregator");
                          return InternalValidationResult.REJECT;
                        }
                        if (!beaconCommittee.contains(
                            toIntExact(aggregateAndProof.getIndex().longValue()))) {
                          LOG.trace(
                              "Rejecting aggregate because attester is not in committee. Should have been one of {}",
                              beaconCommittee);
                          return InternalValidationResult.REJECT;
                        }

                        if (!validateSignature(
                            signatureVerifier, signedAggregate, state, aggregatorPublicKey.get())) {
                          LOG.trace("Rejecting aggregate with invalid signature");
                          return InternalValidationResult.REJECT;
                        }

                        if (!signatureVerifier.batchVerify()) {
                          LOG.trace("Rejecting aggregate with invalid batch signature");
                          return InternalValidationResult.REJECT;
                        }

                        if (!receivedAggregatorIndexAndEpochs.add(aggregatorIndexAndEpoch)) {
                          LOG.trace("Ignoring duplicate aggregate");
                          return InternalValidationResult.IGNORE;
                        }

                        if (!receivedValidAggregations.add(attestation.hash_tree_root())) {
                          LOG.trace("Ignoring duplicate aggregate based on hash tree root");
                          return InternalValidationResult.IGNORE;
                        }

                        return aggregateInternalValidationResult;
                      });
            });
  }

  private boolean validateSignature(
      final BLSSignatureVerifier signatureVerifier,
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
    return signatureVerifier.verify(
        aggregatorPublicKey, signingRoot, signedAggregate.getSignature());
  }

  private boolean isSelectionProofValid(
      final BLSSignatureVerifier signatureVerifier,
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
    return signatureVerifier.verify(aggregatorPublicKey, signingRoot, selectionProof);
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
