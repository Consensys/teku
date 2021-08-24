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

package tech.pegasys.teku.validator.client.duties.attestations;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

public class AggregationDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final ConcurrentMap<Integer, CommitteeAggregator> aggregatorsByCommitteeIndex =
      new ConcurrentHashMap<>();
  private final UInt64 slot;
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final ValidatorLogger validatorLogger;
  private final SendingStrategy<SignedAggregateAndProof> sendingStrategy;

  public AggregationDuty(
      final UInt64 slot,
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final ValidatorLogger validatorLogger,
      final SendingStrategy<SignedAggregateAndProof> sendingStrategy) {
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.validatorLogger = validatorLogger;
    this.sendingStrategy = sendingStrategy;
  }

  /**
   * Adds a validator to this duty. Only one aggregate per committee will be produced even if
   * multiple validators are added for that committee. The aggregated attestations would be
   * identical anyway so sending all of them would be a waste of network bandwidth.
   *
   * @param validatorIndex the validator's index
   * @param proof the validator's slot signature proving it is the aggregator
   * @param attestationCommitteeIndex the committee index to aggregate
   * @param unsignedAttestationFuture the future returned by {@link
   *     AttestationProductionDuty#addValidator(Validator, int, int, int, int)} which completes with
   *     the unsigned attestation for this committee and slot.
   */
  public void addValidator(
      final Validator validator,
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    aggregatorsByCommitteeIndex.computeIfAbsent(
        attestationCommitteeIndex,
        committeeIndex ->
            new CommitteeAggregator(
                validator,
                UInt64.valueOf(validatorIndex),
                attestationCommitteeIndex,
                proof,
                unsignedAttestationFuture));
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Aggregating attestations at slot {}", slot);
    if (aggregatorsByCommitteeIndex.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return sendingStrategy.send(
        aggregatorsByCommitteeIndex.values().stream().map(this::aggregateCommittee));
  }

  public SafeFuture<ProductionResult<SignedAggregateAndProof>> aggregateCommittee(
      final CommitteeAggregator aggregator) {
    return aggregator
        .unsignedAttestationFuture
        .thenCompose(maybeAttestation -> createAggregate(aggregator, maybeAttestation))
        .exceptionally(
            error -> ProductionResult.failure(aggregator.validator.getPublicKey(), error));
  }

  public SafeFuture<ProductionResult<SignedAggregateAndProof>> createAggregate(
      final CommitteeAggregator aggregator, final Optional<AttestationData> maybeAttestation) {
    if (maybeAttestation.isEmpty()) {
      return SafeFuture.completedFuture(
          ProductionResult.failure(
              aggregator.validator.getPublicKey(),
              new IllegalStateException(
                  "Unable to perform aggregation for committee because no attestation was produced")));
    }
    final AttestationData attestationData = maybeAttestation.get();
    return validatorApiChannel
        .createAggregate(slot, attestationData.hashTreeRoot())
        .thenCompose(
            maybeAggregate -> {
              if (maybeAggregate.isEmpty()) {
                validatorLogger.aggregationSkipped(slot, aggregator.attestationCommitteeIndex);
                return SafeFuture.completedFuture(
                    ProductionResult.noop(aggregator.validator.getPublicKey()));
              }
              final Attestation aggregate = maybeAggregate.get();
              return createSignedAggregateAndProof(aggregator, aggregate);
            });
  }

  private SafeFuture<ProductionResult<SignedAggregateAndProof>> createSignedAggregateAndProof(
      final CommitteeAggregator aggregator, final Attestation aggregate) {
    final AggregateAndProof aggregateAndProof =
        new AggregateAndProof(aggregator.validatorIndex, aggregate, aggregator.proof);
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                aggregator
                    .validator
                    .getSigner()
                    .signAggregateAndProof(aggregateAndProof, forkInfo)
                    .thenApply(
                        signature ->
                            ProductionResult.success(
                                aggregator.validator.getPublicKey(),
                                aggregateAndProof.getAggregate().getData().getBeacon_block_root(),
                                new SignedAggregateAndProof(aggregateAndProof, signature))));
  }

  private static class CommitteeAggregator {

    private final Validator validator;
    private final UInt64 validatorIndex;
    private final int attestationCommitteeIndex;
    private final BLSSignature proof;
    private final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture;

    private CommitteeAggregator(
        final Validator validator,
        final UInt64 validatorIndex,
        final int attestationCommitteeIndex,
        final BLSSignature proof,
        final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
      this.validator = validator;
      this.validatorIndex = validatorIndex;
      this.attestationCommitteeIndex = attestationCommitteeIndex;
      this.proof = proof;
      this.unsignedAttestationFuture = unsignedAttestationFuture;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("validator", validator)
          .add("validatorIndex", validatorIndex)
          .add("attestationCommitteeIndex", attestationCommitteeIndex)
          .add("proof", proof)
          .add("unsignedAttestationFuture", unsignedAttestationFuture)
          .toString();
    }
  }
}
