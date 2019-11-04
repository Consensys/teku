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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.artemis.util.sos.MockSimpleOffsetSerializable;

public class GossipTopicHandlerTest {

  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = spy(new EventBus());
  private final MockTopicHandler topicHandler = new MockTopicHandler(publisher, eventBus);

  ArgumentCaptor<ByteBuf> byteBufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
  ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);

  @Test
  public void accept_successfulProcessing() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());

    topicHandler.accept(mockMessage);

    verify(eventBus).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void accept_unsuccessfulProcessing() {
    topicHandler.setShouldSuccessfullyProcessData(false);

    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());

    topicHandler.accept(mockMessage);

    verify(eventBus, never()).post(mockObject);
    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void accept_duplicateMessage() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());

    topicHandler.accept(mockMessage);
    topicHandler.accept(mockMessage);

    // We should only process one message out of the 2 duplicates
    verify(eventBus).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void accept_distinctMessages() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final Bytes data2 = Bytes.fromHexString("0x5678");
    final MockSimpleOffsetSerializable mockObject2 = new MockSimpleOffsetSerializable(data2);
    final Bytes serialized2 = SimpleOffsetSerializer.serialize(mockObject2);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());
    final MessageApi mockMessage2 = new MockMessageApi(serialized2, topicHandler.getTopic());

    topicHandler.accept(mockMessage);
    topicHandler.accept(mockMessage2);

    verify(eventBus).post(mockObject);
    verify(eventBus).post(mockObject2);
    verify(publisher, times(2)).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(byteBufCaptor.getAllValues().get(0).array()).isEqualTo(serialized.toArray());
    assertThat(byteBufCaptor.getAllValues().get(1).array()).isEqualTo(serialized2.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(topicCaptor.getAllValues().get(0)).isEqualTo(topicHandler.getTopic());
    assertThat(topicCaptor.getAllValues().get(1)).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void gossip() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    topicHandler.gossip(mockObject);

    verify(eventBus, never()).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void gossip_duplicateObject() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    topicHandler.gossip(mockObject);
    topicHandler.gossip(mockObject);

    // Should only process the duplicate object once
    verify(eventBus, never()).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void gossip_distinctObjects() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final Bytes data2 = Bytes.fromHexString("0x5678");
    final MockSimpleOffsetSerializable mockObject2 = new MockSimpleOffsetSerializable(data2);
    final Bytes serialized2 = SimpleOffsetSerializer.serialize(mockObject2);

    topicHandler.gossip(mockObject);
    topicHandler.gossip(mockObject2);

    verify(eventBus, never()).post(any());
    verify(publisher, times(2)).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(byteBufCaptor.getAllValues().get(0).array()).isEqualTo(serialized.toArray());
    assertThat(byteBufCaptor.getAllValues().get(1).array()).isEqualTo(serialized2.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(topicCaptor.getAllValues().get(0)).isEqualTo(topicHandler.getTopic());
    assertThat(topicCaptor.getAllValues().get(1)).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void acceptThenGossip_duplicate() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());

    topicHandler.accept(mockMessage);
    topicHandler.gossip(mockObject);

    // Object should be processed once
    verify(eventBus).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void gossipThenAccept_duplicate() {
    final Bytes data = Bytes.fromHexString("0x1234");
    final MockSimpleOffsetSerializable mockObject = new MockSimpleOffsetSerializable(data);
    final Bytes serialized = SimpleOffsetSerializer.serialize(mockObject);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());

    topicHandler.gossip(mockObject);
    topicHandler.accept(mockMessage);

    // Object should be processed once
    verify(eventBus, never()).post(mockObject);
    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  private static class MockTopicHandler extends GossipTopicHandler<MockSimpleOffsetSerializable>
      implements Consumer<MessageApi> {

    private boolean shouldSuccessfullyProcessData = true;

    public MockTopicHandler(final PubsubPublisherApi publisher, final EventBus eventBus) {
      super(publisher, eventBus);
    }

    @Override
    public Topic getTopic() {
      return new Topic("/topic/mock");
    }

    @Override
    protected Optional<MockSimpleOffsetSerializable> processData(
        final MessageApi message, final Bytes bytes) {
      if (!shouldSuccessfullyProcessData) {
        return Optional.empty();
      }
      return Optional.of(new MockSimpleOffsetSerializable(bytes));
    }

    public void setShouldSuccessfullyProcessData(final boolean shouldSuccessfullyProcessData) {
      this.shouldSuccessfullyProcessData = shouldSuccessfullyProcessData;
    }
  }
}
