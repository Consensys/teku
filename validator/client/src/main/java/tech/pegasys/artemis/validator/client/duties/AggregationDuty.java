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

package tech.pegasys.artemis.validator.client.duties;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.Validator;

public class AggregationDuty implements Duty {
  private final ConcurrentMap<Integer, CommitteeAggregator> aggregatorsByCommitteeIndex =
      new ConcurrentHashMap<>();
  private final UnsignedLong slot;
  private final ValidatorApiChannel validatorApiChannel;

  public AggregationDuty(final UnsignedLong slot, final ValidatorApiChannel validatorApiChannel) {
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
  }

  /**
   * Adds a valiator to this duty. Only one aggregate per committee will be produced even if
   * multiple validators are added for that committee. The aggregated attestations would be
   * identical anyway so sending all of them would be a waste of network bandwidth.
   *
   * @param validatorIndex the validator's index
   * @param proof the validator's slot signature proving it is the aggregator
   * @param attestationCommitteeIndex the committee index to aggregate
   * @param unsignedAttestationFuture the future returned by {@link
   *     AttestationProductionDuty#addValidator(Validator, int, int)} which completes with the
   *     unsigned attestation for this committee and slot.
   */
  public void addValidator(
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
    aggregatorsByCommitteeIndex.computeIfAbsent(
        attestationCommitteeIndex,
        committeeIndex -> {
          validatorApiChannel.subscribeToBeaconCommittee(committeeIndex, slot);
          return new CommitteeAggregator(
              UnsignedLong.valueOf(validatorIndex), proof, unsignedAttestationFuture);
        });
  }

  @Override
  public SafeFuture<?> performDuty() {
    return SafeFuture.allOf(
        aggregatorsByCommitteeIndex.values().stream()
            .map(this::aggregateCommittee)
            .toArray(SafeFuture[]::new));
  }

  public SafeFuture<Void> aggregateCommittee(final CommitteeAggregator aggregator) {
    return aggregator
        .unsignedAttestationFuture
        .thenCompose(this::createAggregate)
        .thenAccept(maybeAggregate -> sendAggregate(aggregator, maybeAggregate));
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
    return validatorApiChannel.createAggregate(attestationData);
  }

  private void sendAggregate(
      final CommitteeAggregator aggregator, final Optional<Attestation> maybeAggregate) {
    final Attestation aggregate =
        maybeAggregate.orElseThrow(
            () -> new IllegalStateException("No aggregation could be created"));

    validatorApiChannel.sendAggregateAndProof(
        new AggregateAndProof(aggregator.validatorIndex, aggregator.proof, aggregate));
  }

  @Override
  public String describe() {
    return "Attestation aggregation for slot " + slot;
  }

  private static class CommitteeAggregator {
    private final UnsignedLong validatorIndex;
    private final BLSSignature proof;
    private final SafeFuture<Optional<Attestation>> unsignedAttestationFuture;

    private CommitteeAggregator(
        final UnsignedLong validatorIndex,
        final BLSSignature proof,
        final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
      this.validatorIndex = validatorIndex;
      this.proof = proof;
      this.unsignedAttestationFuture = unsignedAttestationFuture;
    }
  }
}
