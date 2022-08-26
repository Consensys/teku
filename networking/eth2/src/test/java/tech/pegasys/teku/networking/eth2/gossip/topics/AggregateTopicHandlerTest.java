/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AggregateTopicHandlerTest extends AbstractTopicHandlerTest<ValidateableAttestation> {

  @Override
  protected Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        proofMessage ->
            processor.process(
                ValidateableAttestation.aggregateFromNetwork(
                    recentChainData.getSpec(), proofMessage)),
        gossipEncoding,
        forkDigest,
        GossipTopicName.BEACON_AGGREGATE_AND_PROOF,
        Optional.empty(),
        spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema(),
        GOSSIP_MAX_SIZE);
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
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));

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
    Eth2TopicHandler<?> topicHandler = createHandler(forkDigest);
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/11223344/beacon_aggregate_and_proof/ssz_snappy");
  }
}
