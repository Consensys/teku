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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class AggregateTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final GossipedItemConsumer<ValidateableAttestation> attestationConsumer =
      mock(GossipedItemConsumer.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final SignedAggregateAndProofValidator validator =
      mock(SignedAggregateAndProofValidator.class);
  private final AggregateAttestationTopicHandler topicHandler =
      new AggregateAttestationTopicHandler(
          asyncRunner,
          gossipEncoding,
          dataStructureUtil.randomForkInfo(),
          validator,
          attestationConsumer);

  @Test
  public void handleMessage_validAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(validator.validate(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(attestationConsumer).forward(aggregate);
  }

  @Test
  public void handleMessage_savedForFuture() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(validator.validate(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(attestationConsumer).forward(aggregate);
  }

  @Test
  public void handleMessage_ignoredAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(validator.validate(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(attestationConsumer, never()).forward(aggregate);
  }

  @Test
  public void handleMessage_invalidAggregate() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(validator.validate(aggregate))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.REJECT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(
            topicHandler.prepareMessage(
                gossipEncoding.encode(aggregate.getSignedAggregateAndProof())));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(attestationConsumer, never()).forward(aggregate);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    final AggregateAttestationTopicHandler topicHandler =
        new AggregateAttestationTopicHandler(
            asyncRunner, gossipEncoding, forkInfo, validator, attestationConsumer);
    assertThat(topicHandler.getTopic())
        .isEqualTo("/eth2/11223344/beacon_aggregate_and_proof/ssz_snappy");
  }
}
