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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.GossipForkManager.ForkGossipSubscriptions;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.storage.client.RecentChainData;

class GossipForkManagerTest {
  private static final Bytes32 GENESIS_VALIDATORS_ROOT = Bytes32.fromHexString("0x12345678446687");
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
    final ForkGossipSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final GossipForkManager manager = builder().fork(currentForkSubscriptions).build();
    manager.configureGossipForEpoch(UInt64.ZERO);

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
  }

  @Test
  void shouldActivateCurrentAndNextForkOnStartIfNextForkWithinTwoEpochs() {
    final ForkGossipSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final ForkGossipSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(3));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
  }

  @Test
  void shouldActivateMultipleFutureForksIfTheyAreWithinTwoEpochs() {
    final ForkGossipSubscriptions currentFork = forkAtEpoch(0);
    final ForkGossipSubscriptions nextFork = forkAtEpoch(2);
    final ForkGossipSubscriptions laterFork = forkAtEpoch(3);
    final ForkGossipSubscriptions tooLateFork = forkAtEpoch(4);

    managerForForks(currentFork, nextFork, laterFork, tooLateFork)
        .configureGossipForEpoch(UInt64.ONE);

    verify(currentFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(laterFork).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(tooLateFork, never()).startGossip(any());
  }

  @Test
  void shouldNotStartNextForkIfNotWithinTwoEpochs() {
    final ForkGossipSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final ForkGossipSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final GossipForkManager manager =
        managerForForks(currentForkSubscriptions, nextForkSubscriptions);

    manager.configureGossipForEpoch(UInt64.valueOf(2));

    verify(currentForkSubscriptions).startGossip(GENESIS_VALIDATORS_ROOT);
    verify(nextForkSubscriptions, never()).startGossip(any());
  }

  @Test
  void shouldStopActiveSubscriptionsOnStop() {
    final ForkGossipSubscriptions currentForkSubscriptions = forkAtEpoch(0);
    final ForkGossipSubscriptions nextForkSubscriptions = forkAtEpoch(5);
    final ForkGossipSubscriptions laterForkSubscriptions = forkAtEpoch(10);
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
    final ForkGossipSubscriptions genesisFork = forkAtEpoch(0);
    final ForkGossipSubscriptions newFork = forkAtEpoch(5);

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
    final ForkGossipSubscriptions genesisFork = forkAtEpoch(0);
    final ForkGossipSubscriptions newFork = forkAtEpoch(3);
    final ForkGossipSubscriptions laterFork = forkAtEpoch(6);

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

  private ForkGossipSubscriptions forkAtEpoch(final long epoch) {
    final ForkGossipSubscriptions subscriptions =
        mock(ForkGossipSubscriptions.class, "subscriptionsForEpoch" + epoch);
    when(subscriptions.getActivationEpoch()).thenReturn(UInt64.valueOf(epoch));
    return subscriptions;
  }

  private GossipForkManager managerForForks(final ForkGossipSubscriptions... subscriptions) {
    final GossipForkManager.Builder builder = builder();
    Stream.of(subscriptions).forEach(builder::fork);
    return builder.build();
  }

  private GossipForkManager.Builder builder() {
    return GossipForkManager.builder().recentChainData(recentChainData);
  }
}
