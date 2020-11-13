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

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.VoluntaryExitGenerator;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedVoluntaryExit> processor = mock(OperationProcessor.class);

  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(5, recentChainData);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final VoluntaryExitGenerator exitGenerator =
      new VoluntaryExitGenerator(beaconChainUtil.getValidatorKeys());

  private final Eth2TopicHandler<SignedVoluntaryExit> topicHandler =
      new Eth2TopicHandler<>(
          asyncRunner,
          processor,
          gossipEncoding,
          dataStructureUtil.randomForkInfo().getForkDigest(),
          VoluntaryExitGossipManager.TOPIC_NAME,
          SignedVoluntaryExit.class);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_validExit() {
    final SignedVoluntaryExit exit =
        exitGenerator.withEpoch(recentChainData.getBestState().orElseThrow(), 3, 3);
    when(processor.process(exit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_ignoredExit() {
    final SignedVoluntaryExit exit =
        exitGenerator.withEpoch(recentChainData.getBestState().orElseThrow(), 3, 3);
    when(processor.process(exit)).thenReturn(SafeFuture.completedFuture(IGNORE));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final ValidationResult result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized)).join();
    assertThat(result).isEqualTo(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final Eth2TopicHandler<SignedVoluntaryExit> topicHandler =
        new Eth2TopicHandler<>(
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            VoluntaryExitGossipManager.TOPIC_NAME,
            SignedVoluntaryExit.class);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/voluntary_exit/ssz_snappy");
  }
}
