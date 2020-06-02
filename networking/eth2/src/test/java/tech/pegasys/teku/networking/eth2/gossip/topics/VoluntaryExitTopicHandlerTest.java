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
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.VALID;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.VoluntaryExitGenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.VoluntaryExitValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

import java.util.List;

public class VoluntaryExitTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(5, recentChainData);

  private final VoluntaryExitGenerator exitGenerator = new VoluntaryExitGenerator(beaconChainUtil.getValidatorKeys());
  private final VoluntaryExitValidator validator = mock(VoluntaryExitValidator.class);

  private VoluntaryExitTopicHandler topicHandler =
          new VoluntaryExitTopicHandler(
                  gossipEncoding, dataStructureUtil.randomForkInfo(), validator);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_validExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(
            recentChainData.getBestState().orElseThrow(),
            3,
            3
    );
    when(validator.validate(exit)).thenReturn(VALID);
    Bytes serialized = gossipEncoding.encode(exit);
    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(true);
  }

  @Test
  public void handleMessage_invalidExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(
            recentChainData.getBestState().orElseThrow(),
            3,
            3
    );
    when(validator.validate(exit)).thenReturn(INVALID);
    Bytes serialized = gossipEncoding.encode(exit);
    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_invalidBlock_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    final VoluntaryExitTopicHandler topicHandler =
            new VoluntaryExitTopicHandler(gossipEncoding, forkInfo, validator);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/voluntary_exit/ssz_snappy");
  }
}
