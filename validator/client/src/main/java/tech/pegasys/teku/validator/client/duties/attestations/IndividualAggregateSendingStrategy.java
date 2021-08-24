/*
 * Copyright 2021 ConsenSys AG.
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

import static java.util.stream.Collectors.toList;

import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

public class IndividualAggregateSendingStrategy
    implements SendingStrategy<SignedAggregateAndProof> {
  private final ValidatorApiChannel validatorApiChannel;

  public IndividualAggregateSendingStrategy(final ValidatorApiChannel validatorApiChannel) {
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public SafeFuture<DutyResult> send(
      final Stream<SafeFuture<ProductionResult<SignedAggregateAndProof>>> attestations) {
    return DutyResult.combine(
        attestations.map(future -> future.thenCompose(this::sendIfNotFailed)).collect(toList()));
  }

  private SafeFuture<DutyResult> sendIfNotFailed(
      final ProductionResult<SignedAggregateAndProof> result) {
    if (result.failedToProduceMessage()) {
      return SafeFuture.completedFuture(result.getResult());
    } else {
      return sendAttestation(result);
    }
  }

  private SafeFuture<DutyResult> sendAttestation(
      final ProductionResult<SignedAggregateAndProof> result) {
    final SignedAggregateAndProof aggregate = result.getMessage().orElseThrow();
    validatorApiChannel.sendAggregateAndProof(aggregate);
    return SafeFuture.completedFuture(
        DutyResult.success(aggregate.getMessage().getAggregate().getData().getBeacon_block_root()));
  }
}
