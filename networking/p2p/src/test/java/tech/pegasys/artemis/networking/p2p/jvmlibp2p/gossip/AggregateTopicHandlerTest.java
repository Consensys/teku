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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class AggregateTopicHandlerTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = spy(new EventBus());
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);

  private final AggregateTopicHandler topicHandler =
      new AggregateTopicHandler(publisher, eventBus, storageClient);

  ArgumentCaptor<ByteBuf> byteBufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
  ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.initializeStorage(storageClient, validatorKeys);
    doReturn(CompletableFuture.completedFuture(null)).when(publisher).publish(any(), any());
    eventBus.register(topicHandler);
  }

  @Test
  public void onNewAggregate() {
    final AggregateAndProof aggregate = DataStructureUtil.randomAggregateAndProof(1);
    final Bytes serialized = SimpleOffsetSerializer.serialize(aggregate);
    eventBus.post(aggregate);
    // Handler should publish broadcast aggregate

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(topicHandler.getTopic());
  }

  @Test
  public void accept_invalidAttestation_badState() throws Exception {
    final AggregateAndProof aggregate = DataStructureUtil.randomAggregateAndProof(1);
    final Bytes serialized = SimpleOffsetSerializer.serialize(aggregate);

    final MessageApi mockMessage = new MockMessageApi(serialized, topicHandler.getTopic());
    topicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }
}
