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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttesterSlashingValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);

  @SuppressWarnings("unchecked")
  private final GossipedItemConsumer<AttesterSlashing> consumer = mock(GossipedItemConsumer.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(5, recentChainData);

  private final AttesterSlashingValidator validator = mock(AttesterSlashingValidator.class);

  private AttesterSlashingTopicHandler topicHandler =
      new AttesterSlashingTopicHandler(
          asyncRunner, gossipEncoding, dataStructureUtil.randomForkInfo(), validator, consumer);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_validSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(validator.validate(slashing)).thenReturn(ACCEPT);
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(consumer).forward(slashing);
  }

  @Test
  public void handleMessage_ignoredSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(validator.validate(slashing)).thenReturn(IGNORE);
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verifyNoInteractions(consumer);
  }

  @Test
  public void handleMessage_rejectedSlashing() {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(validator.validate(slashing)).thenReturn(REJECT);
    Bytes serialized = gossipEncoding.encode(slashing);
    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(consumer);
  }

  @Test
  public void handleMessage_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(consumer);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    final AttesterSlashingTopicHandler topicHandler =
        new AttesterSlashingTopicHandler(
            asyncRunner, gossipEncoding, forkInfo, validator, consumer);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/attester_slashing/ssz_snappy");
  }
}
