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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Tracks activation of forks and updates the gossip topics that are subscribed to as the fork
 * changes.
 *
 * <p>Each fork needs to have a ForkGossip provided and it's startGossip will be called a few epochs
 * before the fork activates (or at startup if it already has. The stopGossip will be called when
 * the fork is no longer active or during shutdown.
 */
public class GossipForkManager {

  private static final int EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE = 2;
  private final RecentChainData recentChainData;
  private final NavigableMap<UInt64, ForkGossipSubscriptions> forksByActivationEpoch;
  private final Set<ForkGossipSubscriptions> activeSubscriptions = new HashSet<>();

  private Optional<UInt64> currentEpoch = Optional.empty();

  private GossipForkManager(
      final RecentChainData recentChainData,
      final NavigableMap<UInt64, ForkGossipSubscriptions> forksByActivationEpoch) {
    this.recentChainData = recentChainData;
    this.forksByActivationEpoch = forksByActivationEpoch;
  }

  public static GossipForkManager.Builder builder() {
    return new GossipForkManager.Builder();
  }

  public synchronized void configureGossipForEpoch(final UInt64 newEpoch) {
    Optional<UInt64> previousEpoch = currentEpoch;
    if (previousEpoch.isPresent() && previousEpoch.get().isGreaterThanOrEqualTo(newEpoch)) {
      return;
    }
    currentEpoch = Optional.of(newEpoch);

    // Start gossip on current fork
    if (previousEpoch.isEmpty()) {
      // If this is the first call, activate the subscription at the current epoch
      // and any subscriptions for forks happening soon
      startSubscriptions(
          getSubscriptionActiveAt(newEpoch)
              .orElseThrow(() -> new IllegalStateException("No fork active at epoch " + newEpoch)));
      forksByActivationEpoch
          .subMap(newEpoch, false, newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE), true)
          .values()
          .forEach(this::startSubscriptions);
      return;
    }

    // Find subscriptions that are no longer required
    // First find the new forks that activated at least two epochs ago
    final Set<ForkGossipSubscriptions> subscriptionsToStop =
        forksByActivationEpoch
            .subMap(
                previousEpoch.get().minusMinZero(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
                false,
                newEpoch.minusMinZero(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
                true)
            .keySet()
            .stream()
            // Deactivate the fork prior to the newly activated one if any
            .map(forksByActivationEpoch::lowerEntry)
            .filter(Objects::nonNull)
            .map(Entry::getValue)
            .collect(Collectors.toSet());

    // Start subscriptions that will activate soon

    forksByActivationEpoch
        .subMap(
            previousEpoch.get().plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            false,
            newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            true)
        .values()
        // Don't bother starting subscriptions that will be immediately stopped
        .stream()
        .filter(subscription -> !subscriptionsToStop.contains(subscription))
        .forEach(this::startSubscriptions);

    subscriptionsToStop.forEach(this::stopSubscriptions);
  }

  public synchronized void stopGossip() {
    // Stop all active gossips
    activeSubscriptions.forEach(ForkGossipSubscriptions::stopGossip);
    activeSubscriptions.clear();
  }

  private void startSubscriptions(final ForkGossipSubscriptions subscription) {
    if (activeSubscriptions.add(subscription)) {
      subscription.startGossip(
          recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot());
    }
  }

  private void stopSubscriptions(final ForkGossipSubscriptions subscriptions) {
    if (activeSubscriptions.remove(subscriptions)) {
      subscriptions.stopGossip();
    }
  }

  private Optional<ForkGossipSubscriptions> getSubscriptionActiveAt(final UInt64 epoch) {
    return Optional.ofNullable(forksByActivationEpoch.floorEntry(epoch)).map(Map.Entry::getValue);
  }

  public interface ForkGossipSubscriptions {
    UInt64 getActivationEpoch();

    void startGossip(Bytes32 genesisValidatorsRoot);

    void stopGossip();
  }

  public static class Builder {

    private RecentChainData recentChainData;
    private final NavigableMap<UInt64, ForkGossipSubscriptions> forksByActivationEpoch =
        new TreeMap<>();

    public Builder recentChainData(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      return this;
    }

    public Builder fork(final ForkGossipSubscriptions forkSubscriptions) {
      final UInt64 activationEpoch = forkSubscriptions.getActivationEpoch();
      checkState(
          !forksByActivationEpoch.containsKey(activationEpoch),
          "Can not schedule two forks to activate at the same epoch");
      forksByActivationEpoch.put(activationEpoch, forkSubscriptions);
      return this;
    }

    public GossipForkManager build() {
      checkNotNull(recentChainData, "Must supply recentChainData");
      checkState(!forksByActivationEpoch.isEmpty(), "Must specify at least one fork");
      return new GossipForkManager(recentChainData, forksByActivationEpoch);
    }
  }
}
