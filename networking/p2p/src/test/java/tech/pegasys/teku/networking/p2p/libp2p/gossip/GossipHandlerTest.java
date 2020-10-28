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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.GOSSIP_MAX_SIZE;

import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;

public class GossipHandlerTest {
  private final Topic topic = new Topic("Testing");
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final TopicHandler topicHandler = mock(TopicHandler.class);
  private final GossipHandler gossipHandler =
      new GossipHandler(new StubMetricsSystem(), topic, publisher, topicHandler);

  @BeforeEach
  public void setup() {
    when(topicHandler.handleMessage(any()))
        .thenReturn(SafeFuture.completedFuture(ValidationResult.Valid));
    when(publisher.publish(any(), any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  public void apply_valid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void apply_invalid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    when(topicHandler.handleMessage(any()))
        .thenReturn(SafeFuture.completedFuture(ValidationResult.Invalid));
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void apply_exceedsMaxSize() {
    final Bytes data = Bytes.wrap(new byte[GOSSIP_MAX_SIZE + 1]);
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(topicHandler, never()).handleMessage(any());
  }

  @Test
  public void apply_bufferCapacityExceedsMaxSize() {
    ByteBuf data = Unpooled.buffer(GOSSIP_MAX_SIZE + 1).writeBytes(new byte[GOSSIP_MAX_SIZE]);
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void apply_duplicate() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.apply(message);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
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
  @SuppressWarnings("FutureReturnValueIgnored")
  public void gossip_afterDuplicateApply() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.apply(message);
    gossipHandler.gossip(data);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void apply_afterDuplicateGossip() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);

    gossipHandler.gossip(data);
    gossipHandler.apply(message);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(topicHandler, never()).handleMessage(any());
  }

  private ByteBuf toByteBuf(final Bytes bytes) {
    return Unpooled.wrappedBuffer(bytes.toArrayUnsafe());
  }
}
