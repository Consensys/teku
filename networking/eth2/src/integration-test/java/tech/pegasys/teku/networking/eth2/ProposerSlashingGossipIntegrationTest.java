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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ProposerSlashingGossipIntegrationTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipToPeers() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

    // Set up publishers & consumers
    final GossipPublisher<ProposerSlashing> gossipPublisher = new GossipPublisher<>();
    Set<ProposerSlashing> receivedGossip = new HashSet<>();
    final OperationProcessor<ProposerSlashing> operationProcessor =
        (slashing) -> {
          receivedGossip.add(slashing);
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        };

    // Setup network 1
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder =
        b -> b.gossipEncoding(gossipEncoding).proposerSlashingGossipPublisher(gossipPublisher);
    NodeManager node1 = createNodeManager(networkBuilder);

    // Setup network 2
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> b.gossipEncoding(gossipEncoding).gossipedProposerSlashingProcessor(operationProcessor);
    NodeManager node2 = createNodeManager(networkBuilder2);

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Create and publish slashing
    final ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    gossipPublisher.publish(slashing);

    // Verify the slashing was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(receivedGossip).containsExactly(slashing);
        });
  }

  private NodeManager createNodeManager(final Consumer<Eth2P2PNetworkBuilder> networkBuilder)
      throws Exception {
    return NodeManager.create(networkFactory, validatorKeys, networkBuilder);
  }
}
