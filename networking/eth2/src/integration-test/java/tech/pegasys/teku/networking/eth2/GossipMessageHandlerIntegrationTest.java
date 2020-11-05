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
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.computeSubnetForAttestation;
import static tech.pegasys.teku.infrastructure.async.Waiter.ensureConditionRemainsMet;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class GossipMessageHandlerIntegrationTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    final UInt64 blockSlot = UInt64.valueOf(2L);

    // Setup network 1
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder = b -> b.gossipEncoding(gossipEncoding);
    NodeManager node1 = createNodeManager(networkBuilder);
    node1.chainUtil().setSlot(blockSlot);

    // Setup network 2
    Set<SignedBeaconBlock> node2ReceivedBlocks = new HashSet<>();
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b ->
            b.gossipEncoding(gossipEncoding)
                .gossipedBlockProcessor(
                    (block) -> {
                      node2ReceivedBlocks.add(block);
                      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
                    });
    NodeManager node2 = createNodeManager(networkBuilder2);
    node2.chainUtil().setSlot(blockSlot);

    // Setup network 3
    Set<SignedBeaconBlock> node3ReceivedBlocks = new HashSet<>();
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder3 =
        b ->
            b.gossipEncoding(gossipEncoding)
                .gossipedBlockProcessor(
                    (block) -> {
                      node3ReceivedBlocks.add(block);
                      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
                    });
    NodeManager node3 = createNodeManager(networkBuilder3);
    node2.chainUtil().setSlot(blockSlot);

    // Connect networks 1 -> 2 -> 3
    waitFor(node1.connect(node2));
    waitFor(node2.connect(node3));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(2);
          assertThat(node3.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Propagate block from network 1
    final SignedBeaconBlock newBlock = node1.chainUtil().createBlockAtSlot(blockSlot);
    node1.eventBus().post(new ProposedBlockEvent(newBlock));

    // Verify the expected block was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(node2ReceivedBlocks).containsExactly(newBlock);
          assertThat(node3ReceivedBlocks).containsExactly(newBlock);
        });
  }

  @Test
  public void shouldNotGossipInvalidBlocks() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    final UInt64 blockSlot = UInt64.valueOf(2L);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder = b -> b.gossipEncoding(gossipEncoding);

    // Setup network 1
    NodeManager node1 = createNodeManager(networkBuilder);
    node1.chainUtil().setSlot(blockSlot);

    // Setup network 2
    NodeManager node2 = createNodeManager(networkBuilder);
    node2.chainUtil().setSlot(blockSlot);

    // Setup network 3
    NodeManager node3 = createNodeManager(networkBuilder);
    node2.chainUtil().setSlot(blockSlot);

    // Connect networks 1 -> 2 -> 3
    waitFor(node1.connect(node2));
    waitFor(node2.connect(node3));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(2);
          assertThat(node3.network().getPeerCount()).isEqualTo(1);
        });

    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Propagate invalid block from network 1
    final SignedBeaconBlock newBlock =
        node1.chainUtil().createBlockAtSlotFromInvalidProposer(blockSlot);
    node1.eventBus().post(new ProposedBlockEvent(newBlock));

    // Listen for new block event to arrive on networks 2 and 3
    final GossipedBlockCollector network2Blocks = new GossipedBlockCollector(node2.eventBus());
    final GossipedBlockCollector network3Blocks = new GossipedBlockCollector(node3.eventBus());

    // Wait for blocks to propagate
    ensureConditionRemainsMet(() -> assertThat(network2Blocks.getBlocks()).isEmpty(), 10000);
    ensureConditionRemainsMet(() -> assertThat(network3Blocks.getBlocks()).isEmpty(), 10000);
  }

  @Test
  public void shouldNotGossipAttestationsAcrossPeersThatAreNotOnTheSameSubnet() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidateableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final StateAndBlockSummary bestBlockAndState =
        node1.storageClient().getChainHead().orElseThrow();
    Attestation validAttestation = attestationGenerator.validAttestation(bestBlockAndState);
    processedAttestationSubscribers.forEach(
        s -> s.accept(ValidateableAttestation.from(validAttestation)));

    ensureConditionRemainsMet(() -> assertThat(node2attestations).isEmpty());
  }

  @Test
  public void shouldGossipAttestationsAcrossPeersThatAreOnTheSameSubnet() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidateableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final StateAndBlockSummary bestBlockAndState =
        node1.storageClient().getChainHead().orElseThrow();
    ValidateableAttestation validAttestation =
        ValidateableAttestation.from(attestationGenerator.validAttestation(bestBlockAndState));

    final int subnetId =
        computeSubnetForAttestation(
            bestBlockAndState.getState(), validAttestation.getAttestation());
    node1.network().subscribeToAttestationSubnetId(subnetId);
    node2.network().subscribeToAttestationSubnetId(subnetId);

    waitForTopicRegistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    Waiter.waitFor(
        () -> {
          assertThat(node2attestations.size()).isEqualTo(1);
          assertThat(node2attestations.get(0)).isEqualTo(validAttestation);
        });
  }

  @Test
  public void shouldNotGossipAttestationsWhenPeerDeregistersFromTopic() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidateableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (__) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final StateAndBlockSummary bestBlockAndState =
        node1.storageClient().getChainHead().orElseThrow();
    Attestation attestation = attestationGenerator.validAttestation(bestBlockAndState);

    final int subnetId = computeSubnetForAttestation(bestBlockAndState.getState(), attestation);

    ValidateableAttestation validAttestation =
        ValidateableAttestation.fromNetwork(attestation, subnetId);

    node1.network().subscribeToAttestationSubnetId(subnetId);
    node2.network().subscribeToAttestationSubnetId(subnetId);

    waitForTopicRegistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    waitForMessageToBeDelivered();

    assertThat(node2attestations.size()).isEqualTo(1);
    assertThat(node2attestations.get(0)).isEqualTo(validAttestation);

    node1.network().unsubscribeFromAttestationSubnetId(subnetId);
    node2.network().unsubscribeFromAttestationSubnetId(subnetId);

    waitForTopicDeregistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    ensureConditionRemainsMet(
        () -> {
          assertThat(node2attestations.size()).isEqualTo(1);
          assertThat(node2attestations.get(0)).isEqualTo(validAttestation);
        });
  }

  private NodeManager createNodeManager(final Consumer<Eth2P2PNetworkBuilder> networkBuilder)
      throws Exception {
    return NodeManager.create(networkFactory, validatorKeys, networkBuilder);
  }

  private void waitForTopicRegistration() throws Exception {
    Thread.sleep(1000);
  }

  private void waitForTopicDeregistration() throws Exception {
    Thread.sleep(1000);
  }

  private void waitForMessageToBeDelivered() throws Exception {
    Thread.sleep(1000);
  }
}
