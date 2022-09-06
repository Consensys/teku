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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ProposerSlashingTopicHandlerTest extends AbstractTopicHandlerTest<ProposerSlashing> {

  @Override
  protected Eth2TopicHandler<ProposerSlashing> createHandler(final Bytes4 forkDigest) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        GossipTopicName.PROPOSER_SLASHING,
        Optional.empty(),
        ProposerSlashing.SSZ_SCHEMA,
        GOSSIP_MAX_SIZE);
  }

  @Test
  public void handleMessage_validSlashing() {
    final ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
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
    final ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
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
    final ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
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
    Eth2TopicHandler<ProposerSlashing> topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            GossipTopicName.PROPOSER_SLASHING,
            Optional.empty(),
            ProposerSlashing.SSZ_SCHEMA,
            GOSSIP_MAX_SIZE);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/proposer_slashing/ssz_snappy");
  }
}
