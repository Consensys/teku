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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;

public class AggregationDuty implements Duty {
  private final ConcurrentMap<Integer, CommitteeAggregators> aggregatorsByCommitteeIndex =
      new ConcurrentHashMap<>();
  private final UnsignedLong slot;
  private final ValidatorApiChannel validatorApiChannel;

  public AggregationDuty(final UnsignedLong slot, final ValidatorApiChannel validatorApiChannel) {
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
  }

  public void addValidator(
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
    aggregatorsByCommitteeIndex
        .computeIfAbsent(
            attestationCommitteeIndex, __ -> new CommitteeAggregators(unsignedAttestationFuture))
        .addValidator(validatorIndex, proof);
  }

  @Override
  public SafeFuture<?> performDuty() {
    return SafeFuture.allOf(
        aggregatorsByCommitteeIndex.values().stream()
            .map(this::aggregateCommittee)
            .toArray(SafeFuture[]::new));
  }

  public SafeFuture<Void> aggregateCommittee(final CommitteeAggregators aggregators) {
    return aggregators
        .unsignedAttestationFuture
        .thenCompose(
            maybeAttestation -> {
              final AttestationData attestationData =
                  maybeAttestation
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Unable to perform aggregation for committee because no attestation was produced"))
                      .getData();
              return validatorApiChannel.createAggregate(attestationData);
            })
        .thenAccept(maybeAggregate -> sendAggregates(aggregators, maybeAggregate));
  }

  private void sendAggregates(
      final CommitteeAggregators aggregators, final Optional<Attestation> maybeAggregate) {
    final Attestation aggregate =
        maybeAggregate.orElseThrow(
            () -> new IllegalStateException("No aggregation could be created"));
    aggregators.forEach(
        aggregator ->
            validatorApiChannel.sendAggregateAndProof(
                new AggregateAndProof(aggregator.validatorIndex, aggregator.proof, aggregate)));
  }

  @Override
  public String describe() {
    return "Attestation aggregation for slot " + slot;
  }

  private static class CommitteeAggregators {
    private final List<Aggregator> validators = new ArrayList<>();
    private final SafeFuture<Optional<Attestation>> unsignedAttestationFuture;

    private CommitteeAggregators(
        final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
      this.unsignedAttestationFuture = unsignedAttestationFuture;
    }

    public synchronized void addValidator(final int validatorIndex, final BLSSignature proof) {
      validators.add(new Aggregator(UnsignedLong.valueOf(validatorIndex), proof));
    }

    public synchronized void forEach(final Consumer<Aggregator> consumer) {
      validators.forEach(consumer);
    }
  }

  private static class Aggregator {
    private final UnsignedLong validatorIndex;
    private final BLSSignature proof;

    private Aggregator(final UnsignedLong validatorIndex, final BLSSignature proof) {
      this.validatorIndex = validatorIndex;
      this.proof = proof;
    }
  }
}
