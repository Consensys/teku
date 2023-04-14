/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BlobSidecarGossipManagerTest {

  private static final Pattern BLOB_SIDECAR_TOPIC_PATTERN = Pattern.compile("blob_sidecar_(\\d+)");

  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedBlobSidecar> processor = mock(OperationProcessor.class);

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  private final Map<Integer, TopicChannel> topicChannels = new HashMap<>();

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ForkInfo forkInfo =
      new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());

  private BlobSidecarGossipManager blobSidecarGossipManager;

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
    // return TopicChannel mock for each blob_sidecar_<index> topic
    doAnswer(
            i -> {
              final String topicName = i.getArgument(0);
              final TopicChannel topicChannel = mock(TopicChannel.class);
              final Matcher matcher = BLOB_SIDECAR_TOPIC_PATTERN.matcher(topicName);
              if (!matcher.find()) {
                Assertions.fail(
                    BLOB_SIDECAR_TOPIC_PATTERN + " regex does not match the topic: " + topicName);
              }
              final int index = Integer.parseInt(matcher.group(1));
              topicChannels.put(index, topicChannel);
              return topicChannel;
            })
        .when(gossipNetwork)
        .subscribe(any(), any());
    when(processor.process(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    blobSidecarGossipManager =
        BlobSidecarGossipManager.create(
            storageSystem.recentChainData(),
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            processor,
            GOSSIP_MAX_SIZE);
    blobSidecarGossipManager.subscribe();
  }

  @Test
  public void testGossipingBlobSidecarPublishesToCorrectTopic() {
    final SignedBlobSidecar blobSidecar = dataStructureUtil.randomSignedBlobSidecar(UInt64.ONE);
    final Bytes serialized = gossipEncoding.encode(blobSidecar);

    blobSidecarGossipManager.publishBlobSidecar(blobSidecar);

    topicChannels.forEach(
        (index, channel) -> {
          if (index == 1) {
            verify(channel).gossip(serialized);
          } else {
            verifyNoInteractions(channel);
          }
        });
  }

  @Test
  public void testGossipingBlobSidecarWithInvalidIndexDoesNotGossipAnything() {
    final SignedBlobSidecar blobSidecar =
        dataStructureUtil.randomSignedBlobSidecar(UInt64.valueOf(10));

    blobSidecarGossipManager.publishBlobSidecar(blobSidecar);

    topicChannels.forEach((__, channel) -> verifyNoInteractions(channel));
  }

  @Test
  public void testUnsubscribingClosesAllChannels() {
    blobSidecarGossipManager.unsubscribe();

    topicChannels
        .values()
        .forEach(
            channel -> {
              verify(channel).close();
            });
  }

  @Test
  public void tesAcceptingSidecarGossipIfOnTheCorrectTopic() {
    // topic handler for blob sidecars with index 1
    final Eth2TopicHandler<SignedBlobSidecar> topicHandler =
        blobSidecarGossipManager.getTopicHandler(1);

    // processing blob sidecar with index 1 should be accepted
    final SignedBlobSidecar blobSidecar = dataStructureUtil.randomSignedBlobSidecar(UInt64.ONE);
    final InternalValidationResult validationResult =
        SafeFutureAssert.safeJoin(topicHandler.getProcessor().process(blobSidecar));

    assertThat(validationResult).isEqualTo(InternalValidationResult.ACCEPT);
  }

  @Test
  public void testRejectingSidecarGossipIfNotOnTheCorrectTopic() {
    // topic handler for blob sidecars with index 1
    final Eth2TopicHandler<SignedBlobSidecar> topicHandler =
        blobSidecarGossipManager.getTopicHandler(1);

    // processing blob sidecar with index 2 should be rejected
    final SignedBlobSidecar blobSidecar =
        dataStructureUtil.randomSignedBlobSidecar(UInt64.valueOf(2));
    final InternalValidationResult validationResult =
        SafeFutureAssert.safeJoin(topicHandler.getProcessor().process(blobSidecar));

    assertThat(validationResult.isReject()).isTrue();
    assertThat(validationResult.getDescription())
        .hasValue("blob sidecar with index 2 does not match the topic index 1");
  }
}
