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
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;

public class AttestationTopicHandlerTest {

  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = spy(new EventBus());

  private final AttestationTopicHandler topicHandler =
      new AttestationTopicHandler(publisher, eventBus);

  ArgumentCaptor<ByteBuf> byteBufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
  ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);

  @BeforeEach
  public void setup() {
    eventBus.register(topicHandler);
  }

  @Test
  public void onNewAttestation() {
    final Attestation attestation = DataStructureUtil.randomAttestation(1);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);
    eventBus.post(attestation);
    // Handler should publish broadcast attestations

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void accept_validAttestation() {
    final Attestation attestation = DataStructureUtil.randomAttestation(1);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());
    topicHandler.accept(mockMessage);

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Disabled("Gossiped attestations do not yet undergo any validation")
  @Test
  public void accept_invalidAttestation_badData() {
    final Bytes serialized = Bytes.fromHexString("0x3456");

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());
    topicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }
}
