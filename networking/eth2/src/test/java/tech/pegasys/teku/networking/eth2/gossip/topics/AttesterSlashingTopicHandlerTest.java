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
import static tech.pegasys.teku.util.config.Constants.GOSSIP_MAX_SIZE;

import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class AttesterSlashingTopicHandlerTest extends AbstractTopicHandlerTest<AttesterSlashing> {

  @Override
  protected Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        GossipTopicName.ATTESTER_SLASHING,
        AttesterSlashing.SSZ_SCHEMA,
        GOSSIP_MAX_SIZE);
  }

  @Test
  public void handleMessage_validSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(processor.process(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_ignoredSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(processor.process(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_rejectedSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(processor.process(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    Eth2TopicHandler<AttesterSlashing> topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            GossipTopicName.ATTESTER_SLASHING,
            AttesterSlashing.SSZ_SCHEMA,
            GOSSIP_MAX_SIZE);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/attester_slashing/ssz_snappy");
  }
}
