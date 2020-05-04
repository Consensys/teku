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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.util.Waiter.ensureConditionRemainsMet;
import static tech.pegasys.teku.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.util.Waiter;

public class GossipMessageHandlerIntegrationTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    final UnsignedLong blockSlot = UnsignedLong.valueOf(2L);

    // Setup network 1
    NodeManager node1 = NodeManager.create(networkFactory, validatorKeys);
    node1.chainUtil().setSlot(blockSlot);

    // Setup network 2
    NodeManager node2 = NodeManager.create(networkFactory, validatorKeys);
    node2.chainUtil().setSlot(blockSlot);

    // Setup network 3
    NodeManager node3 = NodeManager.create(networkFactory, validatorKeys);
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
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final SignedBeaconBlock newBlock = node1.chainUtil().createBlockAtSlot(blockSlot);
    node1.eventBus().post(new ProposedBlockEvent(newBlock));

    // Listen for new block event to arrive on networks 2 and 3
    final GossipedBlockCollector network2Blocks = new GossipedBlockCollector(node2.eventBus());
    final GossipedBlockCollector network3Blocks = new GossipedBlockCollector(node3.eventBus());

    // Verify the expected block was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(network2Blocks.getBlocks()).containsExactly(newBlock);
          assertThat(network3Blocks.getBlocks()).containsExactly(newBlock);
        });
  }

  @Test
  public void shouldNotGossipInvalidBlocks() throws Exception {
    final UnsignedLong blockSlot = UnsignedLong.valueOf(2L);

    // Setup network 1
    NodeManager node1 = NodeManager.create(networkFactory, validatorKeys);
    node1.chainUtil().setSlot(blockSlot);

    // Setup network 2
    NodeManager node2 = NodeManager.create(networkFactory, validatorKeys);
    node2.chainUtil().setSlot(blockSlot);

    // Setup network 3
    NodeManager node3 = NodeManager.create(networkFactory, validatorKeys);
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

    // TODO: debug this - we shouldn't have to wait here
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
    // Setup network 1
    final NodeManager node1 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(node2.eventBus());

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState bestBlockAndState =
        node1.storageClient().getBestBlockAndState().orElseThrow();
    Attestation validAttestation = attestationGenerator.validAttestation(bestBlockAndState);
    node1.eventBus().post(validAttestation);

    ensureConditionRemainsMet(() -> assertThat(network2Attestations.getAttestations()).isEmpty());
  }

  @Test
  public void shouldGossipAttestationsAcrossPeersThatAreOnTheSameSubnet() throws Exception {
    // Setup network 1
    final NodeManager node1 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(node2.eventBus());

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState bestBlockAndState =
        node1.storageClient().getBestBlockAndState().orElseThrow();
    Attestation validAttestation = attestationGenerator.validAttestation(bestBlockAndState);

    node1
        .network()
        .subscribeToAttestationSubnetId(validAttestation.getData().getIndex().intValue());
    node2
        .network()
        .subscribeToAttestationSubnetId(validAttestation.getData().getIndex().intValue());

    waitForTopicRegistration();

    node1.eventBus().post(validAttestation);

    Waiter.waitFor(
        () -> assertThat(network2Attestations.getAttestations()).containsExactly(validAttestation));
  }

  @Test
  public void shouldNotGossipAttestationsWhenPeerDeregistersFromTopic() throws Exception {
    // Setup network 1
    final NodeManager node1 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = NodeManager.create(networkFactory, validatorKeys);
    final Eth2Network network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(node2.eventBus());

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState bestBlockAndState =
        node1.storageClient().getBestBlockAndState().orElseThrow();
    Attestation validAttestation = attestationGenerator.validAttestation(bestBlockAndState);

    node1
        .network()
        .subscribeToAttestationSubnetId(validAttestation.getData().getIndex().intValue());
    node2
        .network()
        .subscribeToAttestationSubnetId(validAttestation.getData().getIndex().intValue());

    waitForTopicRegistration();

    node1.eventBus().post(validAttestation);

    waitForMessageToBeDelivered();

    assertTrue(network2Attestations.getAttestations().contains(validAttestation));

    node1
        .network()
        .unsubscribeFromAttestationSubnetId(validAttestation.getData().getIndex().intValue());
    node2
        .network()
        .unsubscribeFromAttestationSubnetId(validAttestation.getData().getIndex().intValue());

    waitForTopicDeregistration();

    // Listen if the new attestation arrives on network 2
    final AttestationCollector network2AttestationsAfterDeregistration =
        new AttestationCollector(node2.eventBus());

    ensureConditionRemainsMet(
        () -> assertThat(network2AttestationsAfterDeregistration.getAttestations()).isEmpty());
  }

  private static class AttestationCollector {
    private final Collection<Attestation> attestations = new ConcurrentLinkedQueue<>();

    public AttestationCollector(final EventBus eventBus) {
      eventBus.register(this);
    }

    @Subscribe
    public void onAttestation(final Attestation attestation) {
      attestations.add(attestation);
    }

    public Collection<Attestation> getAttestations() {
      return attestations;
    }
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
