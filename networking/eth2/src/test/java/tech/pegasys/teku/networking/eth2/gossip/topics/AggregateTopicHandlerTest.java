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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.AggregateAttestationTopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AggregateTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final OperationProcessor<ValidateableAttestation> processor =
      mock(OperationProcessor.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final AggregateAttestationTopicHandler topicHandler =
      new AggregateAttestationTopicHandler(
          asyncRunner,
          processor,
          gossipEncoding,
          dataStructureUtil.randomForkInfo().getForkDigest());

  @Test
  public void handleMessage_validAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            dataStructureUtil.randomSignedAggregateAndProof());
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
            dataStructureUtil.randomSignedAggregateAndProof());
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
            dataStructureUtil.randomSignedAggregateAndProof());
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
            dataStructureUtil.randomSignedAggregateAndProof());
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
    final AggregateAttestationTopicHandler topicHandler =
        new AggregateAttestationTopicHandler(asyncRunner, processor, gossipEncoding, forkDigest);
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/11223344/beacon_aggregate_and_proof/ssz_snappy");
  }
}
