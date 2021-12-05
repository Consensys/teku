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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractTopicHandlerTest<T> {
  protected final Spec spec = TestSpecFactory.createMinimalPhase0();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  protected final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  protected final BeaconChainUtil beaconChainUtil = createBeaconChainUtil();

  @SuppressWarnings("unchecked")
  protected final OperationProcessor<T> processor = mock(OperationProcessor.class);

  protected Eth2TopicHandler<?> topicHandler;
  protected Bytes4 forkDigest;

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
    this.forkDigest = recentChainData.getForkDigestByMilestone(SpecMilestone.PHASE0).orElseThrow();
    this.topicHandler = createHandler(forkDigest);
  }

  protected abstract Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest);

  protected BeaconChainUtil createBeaconChainUtil() {
    return BeaconChainUtil.create(spec, 2, recentChainData);
  }
}
