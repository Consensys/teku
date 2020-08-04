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

package tech.pegasys.teku.validator.client.duties;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.validator.client.duties.DutyResult.combine;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.logging.ValidatorLogger;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class AggregationDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final ConcurrentMap<Integer, CommitteeAggregator> aggregatorsByCommitteeIndex =
      new ConcurrentHashMap<>();
  private final UnsignedLong slot;
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final ValidatorLogger validatorLogger;

  public AggregationDuty(
      final UnsignedLong slot,
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final ValidatorLogger validatorLogger) {
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.validatorLogger = validatorLogger;
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
   *     AttestationProductionDuty#addValidator(Validator, int, int, int)} which completes with the
   *     unsigned attestation for this committee and slot.
   */
  public void addValidator(
      final Validator validator,
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
    aggregatorsByCommitteeIndex.computeIfAbsent(
        attestationCommitteeIndex,
        committeeIndex -> {
          validatorApiChannel.subscribeToBeaconCommitteeForAggregation(committeeIndex, slot);
          return new CommitteeAggregator(
              validator, UnsignedLong.valueOf(validatorIndex), proof, unsignedAttestationFuture);
        });
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Aggregating attestations at slot {}", slot);
    return combine(
        aggregatorsByCommitteeIndex.values().stream()
            .map(this::aggregateCommittee)
            .collect(toList()));
  }

  public SafeFuture<DutyResult> aggregateCommittee(final CommitteeAggregator aggregator) {
    return aggregator
        .unsignedAttestationFuture
        .thenCompose(this::createAggregate)
        .thenCompose(maybeAggregate -> sendAggregate(aggregator, maybeAggregate));
  }

  public CompletionStage<Optional<Attestation>> createAggregate(
      final Optional<Attestation> maybeAttestation) {
    final AttestationData attestationData =
        maybeAttestation
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to perform aggregation for committee because no attestation was produced"))
            .getData();
    return validatorApiChannel.createAggregate(attestationData.hashTreeRoot());
  }

  private SafeFuture<DutyResult> sendAggregate(
      final CommitteeAggregator aggregator, final Optional<Attestation> maybeAggregate) {
    if (maybeAggregate.isEmpty()) {
      validatorLogger.aggregationSkipped(slot);
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    final Attestation aggregate = maybeAggregate.get();
    final AggregateAndProof aggregateAndProof =
        new AggregateAndProof(aggregator.validatorIndex, aggregate, aggregator.proof);
    return forkProvider
        .getForkInfo()
        .thenCompose(
            forkInfo ->
                aggregator.validator.getSigner().signAggregateAndProof(aggregateAndProof, forkInfo))
        .thenApply(
            signature -> {
              validatorApiChannel.sendAggregateAndProof(
                  new SignedAggregateAndProof(aggregateAndProof, signature));
              return DutyResult.success(
                  aggregateAndProof.getAggregate().getData().getBeacon_block_root());
            });
  }

  @Override
  public String getProducedType() {
    return "aggregate";
  }

  private static class CommitteeAggregator {

    private final Validator validator;
    private final UnsignedLong validatorIndex;
    private final BLSSignature proof;
    private final SafeFuture<Optional<Attestation>> unsignedAttestationFuture;

    private CommitteeAggregator(
        final Validator validator,
        final UnsignedLong validatorIndex,
        final BLSSignature proof,
        final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
      this.validator = validator;
      this.validatorIndex = validatorIndex;
      this.proof = proof;
      this.unsignedAttestationFuture = unsignedAttestationFuture;
    }

    @Override
    public String toString() {
      return "CommitteeAggregator{"
          + "validator="
          + validator
          + ", validatorIndex="
          + validatorIndex
          + ", proof="
          + proof
          + '}';
    }
  }
}
