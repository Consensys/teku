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

package tech.pegasys.artemis.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.Waiter.ensureConditionRemainsMet;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.statetransition.AttestationGenerator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.events.BlockProposedEvent;
import tech.pegasys.artemis.statetransition.events.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.CommitteeDismissalEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class GossipMessageHandlerIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = ChainStorageClient.memoryOnlyClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(12, storageClient1);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = ChainStorageClient.memoryOnlyClient(eventBus2);
    final Eth2Network network2 =
        networkFactory.eventBus(eventBus2).chainStorageClient(storageClient2).startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Setup network 3
    final EventBus eventBus3 = new EventBus();
    final ChainStorageClient storageClient3 = ChainStorageClient.memoryOnlyClient(eventBus3);
    final Eth2Network network3 =
        networkFactory.eventBus(eventBus3).chainStorageClient(storageClient3).startNetwork();
    chainUtil.initializeStorage(storageClient3);

    // Connect networks 1 -> 2 -> 3
    waitFor(network1.connect(network2.getNodeAddress()));
    waitFor(network2.connect(network3.getNodeAddress()));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(2);
          assertThat(network3.getPeerCount()).isEqualTo(1);
        });
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final BeaconBlock newBlock = chainUtil.createBlockAtSlot(UnsignedLong.valueOf(2L));
    eventBus1.post(new BlockProposedEvent(newBlock));

    // Listen for new block event to arrive on networks 2 and 3
    final GossipedBlockCollector network2Blocks = new GossipedBlockCollector(eventBus2);
    final GossipedBlockCollector network3Blocks = new GossipedBlockCollector(eventBus3);

    // Verify the expected block was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(network2Blocks.getBlocks()).containsExactly(newBlock);
          assertThat(network3Blocks.getBlocks()).containsExactly(newBlock);
        });
  }

  @Test
  public void shouldNotGossipInvalidBlocks() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = ChainStorageClient.memoryOnlyClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(12, storageClient1);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = ChainStorageClient.memoryOnlyClient(eventBus2);
    final Eth2Network network2 =
        networkFactory.eventBus(eventBus2).chainStorageClient(storageClient2).startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Setup network 3
    final EventBus eventBus3 = new EventBus();
    final ChainStorageClient storageClient3 = ChainStorageClient.memoryOnlyClient(eventBus3);
    final Eth2Network network3 =
        networkFactory.eventBus(eventBus3).chainStorageClient(storageClient3).startNetwork();
    chainUtil.initializeStorage(storageClient3);

    // Connect networks 1 -> 2 -> 3
    waitFor(network1.connect(network2.getNodeAddress()));
    waitFor(network2.connect(network3.getNodeAddress()));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(2);
          assertThat(network3.getPeerCount()).isEqualTo(1);
        });
    // TODO: debug this - we shouldn't have to wait here
    Thread.sleep(2000);

    // Propagate block from network 1
    final BeaconBlock newBlock =
        chainUtil.createBlockAtSlotFromInvalidProposer(UnsignedLong.valueOf(2L));
    eventBus1.post(new BlockProposedEvent(newBlock));

    // Listen for new block event to arrive on networks 2 and 3
    final GossipedBlockCollector network2Blocks = new GossipedBlockCollector(eventBus2);
    final GossipedBlockCollector network3Blocks = new GossipedBlockCollector(eventBus3);

    // Wait for blocks to propagate
    ensureConditionRemainsMet(() -> assertThat(network2Blocks.getBlocks()).isEmpty(), 10000);
    ensureConditionRemainsMet(() -> assertThat(network3Blocks.getBlocks()).isEmpty(), 10000);
  }

  @Test
  public void shouldNotGossipAttestationsAcrossPeersThatAreNotOnTheSameSubnet() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = ChainStorageClient.memoryOnlyClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();
    List<BLSKeyPair> blsKeyPairList =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 12);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient1, blsKeyPairList);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = ChainStorageClient.memoryOnlyClient(eventBus2);
    final Eth2Network network2 =
        networkFactory.eventBus(eventBus2).chainStorageClient(storageClient2).startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Connect networks 1 -> 2
    waitFor(network1.connect(network2.getNodeAddress()));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(eventBus2);

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(blsKeyPairList);
    Attestation validAttestation = attestationGenerator.validAttestation(storageClient1);
    eventBus1.post(validAttestation);

    ensureConditionRemainsMet(() -> assertThat(network2Attestations.getAttestations()).isEmpty());
  }

  @Test
  public void shouldGossipAttestationsAcrossPeersThatAreOnTheSameSubnet() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = ChainStorageClient.memoryOnlyClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();
    List<BLSKeyPair> blsKeyPairList =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 12);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient1, blsKeyPairList);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = ChainStorageClient.memoryOnlyClient(eventBus2);
    final Eth2Network network2 =
        networkFactory.eventBus(eventBus2).chainStorageClient(storageClient2).startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Connect networks 1 -> 2
    waitFor(network1.connect(network2.getNodeAddress()));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(eventBus2);

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(blsKeyPairList);
    Attestation validAttestation = attestationGenerator.validAttestation(storageClient1);

    eventBus1.post(
        new CommitteeAssignmentEvent(List.of(validAttestation.getData().getIndex().intValue())));
    eventBus2.post(
        new CommitteeAssignmentEvent(List.of(validAttestation.getData().getIndex().intValue())));

    waitForTopicRegistration();

    eventBus1.post(validAttestation);

    Waiter.waitFor(
        () -> assertThat(network2Attestations.getAttestations()).containsExactly(validAttestation));
  }

  @Test
  public void shouldNotGossipAttestationsWhenPeerDeregistersFromTopic() throws Exception {
    // Setup network 1
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = ChainStorageClient.memoryOnlyClient(eventBus1);
    final Eth2Network network1 =
        networkFactory.eventBus(eventBus1).chainStorageClient(storageClient1).startNetwork();
    List<BLSKeyPair> blsKeyPairList =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 12);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient1, blsKeyPairList);
    chainUtil.initializeStorage();

    // Setup network 2
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = ChainStorageClient.memoryOnlyClient(eventBus2);
    final Eth2Network network2 =
        networkFactory.eventBus(eventBus2).chainStorageClient(storageClient2).startNetwork();
    chainUtil.initializeStorage(storageClient2);

    // Connect networks 1 -> 2
    waitFor(network1.connect(network2.getNodeAddress()));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Listen for new attestation event to arrive on network 2
    final AttestationCollector network2Attestations = new AttestationCollector(eventBus2);

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(blsKeyPairList);
    Attestation validAttestation = attestationGenerator.validAttestation(storageClient1);

    eventBus1.post(
        new CommitteeAssignmentEvent(List.of(validAttestation.getData().getIndex().intValue())));
    eventBus2.post(
        new CommitteeAssignmentEvent(List.of(validAttestation.getData().getIndex().intValue())));

    waitForTopicRegistration();

    eventBus1.post(validAttestation);

    waitForMessageToBeDelivered();

    assertTrue(network2Attestations.getAttestations().contains(validAttestation));

    eventBus1.post(
        new CommitteeDismissalEvent(List.of(validAttestation.getData().getIndex().intValue())));
    eventBus2.post(
        new CommitteeDismissalEvent(List.of(validAttestation.getData().getIndex().intValue())));

    waitForTopicDeregistration();

    // Listen if the new attestation arrives on network 2
    final AttestationCollector network2AttestationsAfterDeregistration =
        new AttestationCollector(eventBus2);

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
