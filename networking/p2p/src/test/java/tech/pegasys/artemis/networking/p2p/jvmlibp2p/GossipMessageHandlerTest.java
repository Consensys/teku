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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip.GossipMessageHandler;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

class GossipMessageHandlerTest {
  private static final Topic BLOCKS_TOPIC = new Topic("/eth2/beacon_block/ssz");
  private static final Topic ATTESTATIONS_TOPIC = new Topic("/eth2/beacon_attestation/ssz");

  private static final BeaconBlock BLOCK1 = DataStructureUtil.randomBeaconBlock(1, 100);
  private static final BeaconBlock BLOCK2 = DataStructureUtil.randomBeaconBlock(2, 100);
  private static final Attestation ATTESTATION1 = DataStructureUtil.randomAttestation(3);
  private static final Attestation ATTESTATION2 = DataStructureUtil.randomAttestation(4);

  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = mock(EventBus.class);

  private final GossipMessageHandler handler = new GossipMessageHandler(publisher, eventBus);

  @Test
  public void shouldGossipProcessedBlocks() {
    handler.onNewBlock(BLOCK1);
    verifyMessagePublished(BLOCK1, BLOCKS_TOPIC);

    handler.onNewBlock(BLOCK2);
    verifyMessagePublished(BLOCK2, BLOCKS_TOPIC);
  }

  @Test
  public void shouldNotGossipBlockMoreThanOnce() {
    handler.onNewBlock(BLOCK1);
    verifyMessagePublished(BLOCK1, BLOCKS_TOPIC);
    handler.onNewBlock(BLOCK1);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  public void shouldGossipProcessedAttestations() {
    handler.onNewAttestation(ATTESTATION1);
    verifyMessagePublished(ATTESTATION1, ATTESTATIONS_TOPIC);

    handler.onNewAttestation(ATTESTATION2);
    verifyMessagePublished(ATTESTATION2, ATTESTATIONS_TOPIC);
  }

  @Test
  public void shouldNotGossipAttestationMoreThanOnce() {
    handler.onNewAttestation(ATTESTATION1);
    verifyMessagePublished(ATTESTATION1, ATTESTATIONS_TOPIC);
    handler.onNewAttestation(ATTESTATION1);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  public void shouldSendEventWhenBlockReceived() {
    final MessageApi message = mock(MessageApi.class);
    when(message.getTopics()).thenReturn(singletonList(BLOCKS_TOPIC));
    when(message.getData()).thenReturn(serialize(BLOCK1));
    handler.accept(message);

    verify(eventBus).post(BLOCK1);
  }

  @Test
  public void shouldIgnoreUnknownMessages() {
    final MessageApi message = mock(MessageApi.class);
    when(message.getTopics()).thenReturn(singletonList(new Topic("/eth2/unknown/ssz")));
    when(message.getData()).thenReturn(Unpooled.buffer(10));
    handler.accept(message);

    verifyZeroInteractions(publisher);
    verifyZeroInteractions(eventBus);
  }

  private void verifyMessagePublished(
      final SimpleOffsetSerializable toSerialize, final Topic topic) {
    final ByteBuf expectedData = serialize(toSerialize);
    verify(publisher).publish(expectedData, topic);
  }

  private ByteBuf serialize(final SimpleOffsetSerializable toSerialize) {
    return Unpooled.wrappedBuffer(SimpleOffsetSerializer.serialize(toSerialize).toArrayUnsafe());
  }
}
