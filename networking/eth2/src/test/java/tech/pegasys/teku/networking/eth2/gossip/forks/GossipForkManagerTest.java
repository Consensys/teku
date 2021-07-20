/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip.forks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class GossipForkManagerTest {
  private static final Bytes32 GENESIS_VALIDATORS_ROOT = Bytes32.fromHexString("0x12345678446687");
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @BeforeEach
  void setUp() {
    when(recentChainData.getGenesisData())
        .thenReturn(
            Optional.of(new GenesisData(UInt64.valueOf(134234134L), GENESIS_VALIDATORS_ROOT)));
  }

  @Test
  void shouldThrowExceptionIfNoForksRegistered() {
    assertThatThrownBy(() -> builder().build()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowExceptionIfNoForkActiveAtStartingEpoch() {
    final GossipForkManager manager = builder().fork(forkAtEpoch(6)).build();
    assertThatThrownBy(() -> manager.configureGossipForEpoch(UInt64.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No fork active at epoch 0");
  }

  @Test
  void shouldActivateCurrentForkOnStart() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkManager manager = builder().fork(currentForkSubscriptions).build();
    manager.configureGossipForEpoch(UInt64.ZERO);

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
  }

  @Test
  void shouldActivateCurrentAndNextForkOnStartIfNextForkWithinTwoEpochs() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(3));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
  }

  @Test
  void shouldActivateMultipleFutureForksIfTheyAreWithinTwoEpochs() {
    final GossipForkSubscriptions currentFork = forkAtEpoch(0);
    final GossipForkSubscriptions nextFork = forkAtEpoch(2);
    final GossipForkSubscriptions laterFork = forkAtEpoch(3);
    final GossipForkSubscriptions tooLateFork = forkAtEpoch(4);

    managerForForks(currentFork, nextFork, laterFork, tooLateFork)
        .configureGossipForEpoch(UInt64.ONE);

    verify(currentFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(laterFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(tooLateFork, never()).startGossip(any());
  }

  @Test
  void shouldNotStartNextForkIfNotWithinTwoEpochs() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(2));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextForkSubscriptions, never()).startGossip(any());
  }

  @Test
  void shouldStopActiveSubscriptionsOnStop() {
    final GossipForkSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkSubscriptions laterForkSubscriptions = forkAtEpoch(10);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions, laterForkSubscriptions);
    manager.configureGossipForEpoch(UInt64.valueOf(3));

    manager.stopGossip();

    verify(currentForkSubscriptions).stopGossip();
    verify(nextForkSubscriptions).stopGossip();
    verify(laterForkSubscriptions, never()).stopGossip();
  }

  @Test
  void shouldStopForkTwoEpochsAfterTheNextOneActivates() {
    final GossipForkSubscriptions genesisFork = forkAtEpoch(0);
    final GossipForkSubscriptions newFork = forkAtEpoch(5);

    final GossipForkManager manager = managerForForks(genesisFork, newFork);
    manager.configureGossipForEpoch(UInt64.valueOf(4));

    verify(genesisFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(newFork).startGossip(GENESIS_VALIDATORS_ROOT);

    // Shouldn't make any changes in epochs 5 or 6
    manager.configureGossipForEpoch(UInt64.valueOf(5));
    manager.configureGossipForEpoch(UInt64.valueOf(6));
    verify(genesisFork, times(1)).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(newFork, times(1)).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(genesisFork, never()).stopGossip();
    verify(newFork, never()).stopGossip();

    // Should stop the genesis fork at epoch 7
    manager.configureGossipForEpoch(UInt64.valueOf(7));
    verify(genesisFork).stopGossip();
    verify(newFork, never()).stopGossip();
  }

  @Test
  void shouldProcessForkChangesWhenEpochsAreMissed() {
    // We may skip epochs if we fall behind and skip slots to catch up
    final GossipForkSubscriptions genesisFork = forkAtEpoch(0);
    final GossipForkSubscriptions newFork = forkAtEpoch(3);
    final GossipForkSubscriptions laterFork = forkAtEpoch(6);

    final GossipForkManager manager = managerForForks(genesisFork, newFork, laterFork);

    // Should start the genesis subscriptions on first call
    manager.configureGossipForEpoch(UInt64.ZERO);
    verify(genesisFork).startGossip(GENESIS_VALIDATORS_ROOT);

    // Jump to epoch 10 and should wind up with only laterFork active
    manager.configureGossipForEpoch(UInt64.valueOf(10));
    verify(genesisFork).stopGossip();

    // No point starting newFork as it's already due to be stopped
    verify(newFork, never()).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(newFork, never()).stopGossip();

    verify(laterFork).startGossip(GENESIS_VALIDATORS_ROOT);
  }

  @Test
  void shouldPublishAttestationToForkForAttestationsSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidateableAttestation firstForkAttestation =
        ValidateableAttestation.fromValidator(spec, dataStructureUtil.randomAttestation(0));
    final ValidateableAttestation secondForkAttestation =
        ValidateableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(UInt64.ONE).longValue()));
    final ValidateableAttestation thirdForkAttestation =
        ValidateableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(UInt64.valueOf(2)).longValue()));

    manager.publishAttestation(firstForkAttestation);
    verify(firstFork).publishAttestation(firstForkAttestation);
    verify(secondFork, never()).publishAttestation(firstForkAttestation);
    verify(thirdFork, never()).publishAttestation(firstForkAttestation);

    manager.publishAttestation(secondForkAttestation);
    verify(firstFork, never()).publishAttestation(secondForkAttestation);
    verify(secondFork).publishAttestation(secondForkAttestation);
    verify(thirdFork, never()).publishAttestation(secondForkAttestation);

    manager.publishAttestation(thirdForkAttestation);
    verify(firstFork, never()).publishAttestation(thirdForkAttestation);
    verify(secondFork, never()).publishAttestation(thirdForkAttestation);
    verify(thirdFork).publishAttestation(thirdForkAttestation);
  }

  @Test
  void shouldNotPublishAttestationsToForksThatAreNotActive() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);

    final GossipForkManager manager = managerForForks(firstFork, secondFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidateableAttestation attestation =
        ValidateableAttestation.fromValidator(
            spec,
            dataStructureUtil.randomAttestation(
                spec.computeStartSlotAtEpoch(secondFork.getActivationEpoch()).longValue()));

    manager.publishAttestation(attestation);

    verify(firstFork, never()).publishAttestation(attestation);
    verify(secondFork, never()).publishAttestation(attestation);
  }

  @Test
  void shouldPublishBlockToForkForBlockSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final SignedBeaconBlock firstForkBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final SignedBeaconBlock secondForkBlock =
        dataStructureUtil.randomSignedBeaconBlock(spec.computeStartSlotAtEpoch(UInt64.ONE));
    final SignedBeaconBlock thirdForkBlock =
        dataStructureUtil.randomSignedBeaconBlock(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));

    manager.publishBlock(firstForkBlock);
    verify(firstFork).publishBlock(firstForkBlock);
    verify(secondFork, never()).publishBlock(firstForkBlock);
    verify(thirdFork, never()).publishBlock(firstForkBlock);

    manager.publishBlock(secondForkBlock);
    verify(firstFork, never()).publishBlock(secondForkBlock);
    verify(secondFork).publishBlock(secondForkBlock);
    verify(thirdFork, never()).publishBlock(secondForkBlock);

    manager.publishBlock(thirdForkBlock);
    verify(firstFork, never()).publishBlock(thirdForkBlock);
    verify(secondFork, never()).publishBlock(thirdForkBlock);
    verify(thirdFork).publishBlock(thirdForkBlock);
  }

  @Test
  void shouldPublishSyncCommitteeMessageToForkForSignatureSlot() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(1);
    final GossipForkSubscriptions thirdFork = forkAtEpoch(2);

    final GossipForkManager manager = managerForForks(firstFork, secondFork, thirdFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidateableSyncCommitteeMessage firstForkMessage =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(0));
    final ValidateableSyncCommitteeMessage secondForkMessage =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(spec.computeStartSlotAtEpoch(UInt64.ONE)));
    final ValidateableSyncCommitteeMessage thirdForkMessage =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                spec.computeStartSlotAtEpoch(UInt64.valueOf(2))));

    manager.publishSyncCommitteeMessage(firstForkMessage);
    verify(firstFork).publishSyncCommitteeMessage(firstForkMessage);
    verify(secondFork, never()).publishSyncCommitteeMessage(firstForkMessage);
    verify(thirdFork, never()).publishSyncCommitteeMessage(firstForkMessage);

    manager.publishSyncCommitteeMessage(secondForkMessage);
    verify(firstFork, never()).publishSyncCommitteeMessage(secondForkMessage);
    verify(secondFork).publishSyncCommitteeMessage(secondForkMessage);
    verify(thirdFork, never()).publishSyncCommitteeMessage(secondForkMessage);

    manager.publishSyncCommitteeMessage(thirdForkMessage);
    verify(firstFork, never()).publishSyncCommitteeMessage(thirdForkMessage);
    verify(secondFork, never()).publishSyncCommitteeMessage(thirdForkMessage);
    verify(thirdFork).publishSyncCommitteeMessage(thirdForkMessage);
  }

  @Test
  void shouldNotPublishSyncCommitteeMessagesToForksThatAreNotActive() {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);

    final GossipForkManager manager = managerForForks(firstFork, secondFork);
    manager.configureGossipForEpoch(UInt64.ZERO);

    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                spec.computeStartSlotAtEpoch(secondFork.getActivationEpoch())));

    manager.publishSyncCommitteeMessage(message);

    verify(firstFork, never()).publishSyncCommitteeMessage(message);
    verify(secondFork, never()).publishSyncCommitteeMessage(message);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeToAttestationSubnetsPriorToStarting(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions fork = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(fork);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.verifySubscribe(fork, 1);
    subscriptionType.verifySubscribe(fork, 2);
    subscriptionType.verifySubscribe(fork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeToCurrentAttestationSubnetsWhenNewForkActivates(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    manager.configureGossipForEpoch(UInt64.valueOf(8));

    verify(secondFork).startGossip(GENESIS_VALIDATORS_ROOT);
    subscriptionType.verifySubscribe(secondFork, 1);
    subscriptionType.verifySubscribe(secondFork, 2);
    subscriptionType.verifySubscribe(secondFork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldSubscribeActiveForksToAttestationSubnets(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.verifySubscribe(firstFork, 1);
    subscriptionType.verifySubscribe(firstFork, 2);
    subscriptionType.verifySubscribe(firstFork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldUnsubscribeActiveForksFromAttestationSubnets(final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.verifySubscribe(firstFork, 1);

    subscriptionType.unsubscribe(manager, 1);
    subscriptionType.verifyUnsubscribe(firstFork, 1);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldNotSubscribeToSubnetThatWasUnsubscribedPriorToStarting(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions fork = forkAtEpoch(0);
    final GossipForkManager manager = managerForForks(fork);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.unsubscribe(manager, 2);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.verifySubscribe(fork, 1);
    subscriptionType.verifyNotSubscribed(fork, 2);
    subscriptionType.verifySubscribe(fork, 5);
  }

  @ParameterizedTest
  @MethodSource("subnetSubscriptionTypes")
  void shouldNotSubscribeToSubnetThatWasUnsubscribedWhenNewForkActivates(
      final SubscriptionType subscriptionType) {
    final GossipForkSubscriptions firstFork = forkAtEpoch(0);
    final GossipForkSubscriptions secondFork = forkAtEpoch(10);
    final GossipForkManager manager = managerForForks(firstFork, secondFork);

    manager.configureGossipForEpoch(UInt64.ZERO);

    subscriptionType.subscribe(manager, 1);
    subscriptionType.subscribe(manager, 2);
    subscriptionType.subscribe(manager, 5);

    subscriptionType.unsubscribe(manager, 2);

    manager.configureGossipForEpoch(UInt64.valueOf(8));

    verify(secondFork).startGossip(GENESIS_VALIDATORS_ROOT);
    subscriptionType.verifySubscribe(secondFork, 1);
    subscriptionType.verifyNotSubscribed(secondFork, 2);
    subscriptionType.verifySubscribe(secondFork, 5);
  }

  private GossipForkSubscriptions forkAtEpoch(final long epoch) {
    final GossipForkSubscriptions subscriptions =
        mock(GossipForkSubscriptions.class, "subscriptionsForEpoch" + epoch);
    when(subscriptions.getActivationEpoch()).thenReturn(UInt64.valueOf(epoch));
    return subscriptions;
  }

  private GossipForkManager managerForForks(final GossipForkSubscriptions... subscriptions) {
    final GossipForkManager.Builder builder = builder();
    Stream.of(subscriptions).forEach(builder::fork);
    return builder.build();
  }

  private GossipForkManager.Builder builder() {
    return GossipForkManager.builder().recentChainData(recentChainData).spec(spec);
  }

  static Stream<SubscriptionType> subnetSubscriptionTypes() {
    return Stream.of(
        new SubscriptionType(
            "attestation",
            GossipForkManager::subscribeToAttestationSubnetId,
            GossipForkManager::unsubscribeFromAttestationSubnetId,
            (manager, subnetId) -> verify(manager).subscribeToAttestationSubnetId(subnetId),
            (manager, subnetId) ->
                verify(manager, never()).subscribeToAttestationSubnetId(subnetId),
            (manager, subnetId) -> verify(manager).unsubscribeFromAttestationSubnetId(subnetId)),
        new SubscriptionType(
            "sync committee",
            GossipForkManager::subscribeToSyncCommitteeSubnetId,
            GossipForkManager::unsubscribeFromSyncCommitteeSubnetId,
            (manager, subnetId) -> verify(manager).subscribeToSyncCommitteeSubnet(subnetId),
            (manager, subnetId) ->
                verify(manager, never()).subscribeToSyncCommitteeSubnet(subnetId),
            (manager, subnetId) -> verify(manager).unsubscribeFromSyncCommitteeSubnet(subnetId)));
  }

  private static class SubscriptionType {
    private final String type;
    private final BiConsumer<GossipForkManager, Integer> subscribeToSubnet;
    private final BiConsumer<GossipForkManager, Integer> unsubscribeFromSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifySubscribeToSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifyNotSubscribedToSubnet;
    private final BiConsumer<GossipForkSubscriptions, Integer> verifyUnsubscribeFromSubnet;

    private SubscriptionType(
        final String type,
        final BiConsumer<GossipForkManager, Integer> subscribeToSubnet,
        final BiConsumer<GossipForkManager, Integer> unsubscribeFromSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifySubscribeToSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifyNotSubscribedToSubnet,
        final BiConsumer<GossipForkSubscriptions, Integer> verifyUnsubscribeFromSubnet) {
      this.type = type;
      this.subscribeToSubnet = subscribeToSubnet;
      this.unsubscribeFromSubnet = unsubscribeFromSubnet;
      this.verifySubscribeToSubnet = verifySubscribeToSubnet;
      this.verifyNotSubscribedToSubnet = verifyNotSubscribedToSubnet;
      this.verifyUnsubscribeFromSubnet = verifyUnsubscribeFromSubnet;
    }

    public void subscribe(final GossipForkManager manager, final int subnetId) {
      subscribeToSubnet.accept(manager, subnetId);
    }

    public void unsubscribe(final GossipForkManager manager, final int subnetId) {
      unsubscribeFromSubnet.accept(manager, subnetId);
    }

    public void verifySubscribe(final GossipForkSubscriptions fork, final int subnetId) {
      verifySubscribeToSubnet.accept(fork, subnetId);
    }

    public void verifyNotSubscribed(final GossipForkSubscriptions fork, final int subnetId) {
      verifyNotSubscribedToSubnet.accept(fork, subnetId);
    }

    public void verifyUnsubscribe(final GossipForkSubscriptions fork, final int subnetId) {
      verifyUnsubscribeFromSubnet.accept(fork, subnetId);
    }

    @Override
    public String toString() {
      return type;
    }
  }
}
