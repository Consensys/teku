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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.AggregateAttestationTopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AggregateTopicHandlerTest extends AbstractTopicHandlerTest<ValidateableAttestation> {

  @Override
  protected Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest) {
    return AggregateAttestationTopicHandler.createHandler(
        recentChainData, asyncRunner, processor, gossipEncoding, forkDigest);
  }

  @Test
  public void handleMessage_validAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(processor.process(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_savedForFuture() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(processor.process(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_ignoredAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(processor.process(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(processor.process(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.REJECT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    Eth2TopicHandler<?> topicHandler =
        AggregateAttestationTopicHandler.createHandler(
            recentChainData, asyncRunner, processor, gossipEncoding, forkDigest);
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/11223344/beacon_aggregate_and_proof/ssz_snappy");
  }
}
