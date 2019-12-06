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

package tech.pegasys.artemis.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.artemis.networking.p2p.gossip.TopicHandler;

public class GossipHandlerTest {
  private final Topic topic = new Topic("Testing");
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final TopicHandler topicHandler = mock(TopicHandler.class);
  private final GossipHandler gossipHandler = new GossipHandler(topic, publisher, topicHandler);

  @BeforeEach
  public void setup() {
    doReturn(true).when(topicHandler).handleMessage(any());
    doReturn(CompletableFuture.completedFuture(null)).when(publisher).publish(any(), any());
  }

  @Test
  public void apply_valid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    final CompletableFuture<Boolean> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  public void apply_invalid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    doReturn(false).when(topicHandler).handleMessage(any());
    final CompletableFuture<Boolean> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void apply_exceedsMaxSize() {
    final Bytes data = Bytes.wrap(new byte[GossipHandler.GOSSIP_MAX_SIZE + 1]);
    final MockMessageApi message = new MockMessageApi(data, topic);
    final CompletableFuture<Boolean> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(false);
    verify(topicHandler, never()).handleMessage(any());
  }

  @Test
  public void apply_duplicate() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.apply(message);
    final CompletableFuture<Boolean> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(false);
    verify(topicHandler).handleMessage(any());
  }

  @Test
  public void gossip_newMessage() {
    final Bytes message = Bytes.fromHexString("0x01");
    gossipHandler.gossip(message);
    verify(publisher).publish(toByteBuf(message), topic);
  }

  @Test
  public void gossip_duplicateMessage() {
    final Bytes message = Bytes.fromHexString("0x01");
    gossipHandler.gossip(message);
    gossipHandler.gossip(message);
    verify(publisher).publish(toByteBuf(message), topic);
  }

  @Test
  public void gossip_distinctMessages() {
    final Bytes message1 = Bytes.fromHexString("0x01");
    final Bytes message2 = Bytes.fromHexString("0x01");
    gossipHandler.gossip(message1);
    gossipHandler.gossip(message2);
    verify(publisher).publish(toByteBuf(message1), topic);
    verify(publisher).publish(toByteBuf(message2), topic);
  }

  @Test
  public void gossip_afterDuplicateApply() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.apply(message);
    gossipHandler.gossip(data);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void apply_afterDuplicateGossip() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.gossip(data);
    gossipHandler.apply(message);
    final CompletableFuture<Boolean> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(false);
    verify(topicHandler, never()).handleMessage(any());
  }

  private ByteBuf toByteBuf(final Bytes bytes) {
    return Unpooled.wrappedBuffer(bytes.toArrayUnsafe());
  }
}
