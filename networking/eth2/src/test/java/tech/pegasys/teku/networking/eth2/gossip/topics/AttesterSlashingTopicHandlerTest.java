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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<AttesterSlashing> processor = mock(OperationProcessor.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(5, recentChainData);

  private Eth2TopicHandler<AttesterSlashing> topicHandler =
      new Eth2TopicHandler<>(
          asyncRunner,
          processor,
          gossipEncoding,
          dataStructureUtil.randomForkInfo().getForkDigest(),
          AttesterSlashingGossipManager.TOPIC_NAME,
          AttesterSlashing.class);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
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
    when(processor.process(slashing)).thenReturn(SafeFuture.completedFuture(IGNORE));
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_rejectedSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(processor.process(slashing)).thenReturn(SafeFuture.completedFuture(REJECT));
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
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            AttesterSlashingGossipManager.TOPIC_NAME,
            AttesterSlashing.class);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/attester_slashing/ssz_snappy");
  }
}
