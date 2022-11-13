/*
 * Copyright ConsenSys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
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

  private static final Logger LOG = LogManager.getLogger();
  private static final int EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE = 2;
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch;
  private final Set<GossipForkSubscriptions> activeSubscriptions = new HashSet<>();
  private final IntSet currentAttestationSubnets = new IntOpenHashSet();
  private final IntSet currentSyncCommitteeSubnets = new IntOpenHashSet();

  private Optional<UInt64> currentEpoch = Optional.empty();

  private GossipForkManager(
      final Spec spec,
      final RecentChainData recentChainData,
      final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch) {
    this.spec = spec;
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
          getSubscriptionActiveAtEpoch(newEpoch)
              .orElseThrow(() -> new IllegalStateException("No fork active at epoch " + newEpoch)));
      forksByActivationEpoch
          .subMap(newEpoch, false, newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE), true)
          .values()
          .forEach(this::startSubscriptions);
      return;
    }

    // Find subscriptions that are no longer required
    // First find the new forks that activated at least two epochs ago
    final Set<GossipForkSubscriptions> subscriptionsToStop =
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
            .map(Map.Entry::getValue)
            .collect(Collectors.toSet());

    // Start subscriptions that will activate soon

    forksByActivationEpoch
        .subMap(
            previousEpoch.get().plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            false,
            newEpoch.plus(EPOCHS_PRIOR_TO_FORK_TO_ACTIVATE),
            true)
        .values()
        .stream()
        // Don't bother starting subscriptions that will be immediately stopped
        .filter(subscription -> !subscriptionsToStop.contains(subscription))
        .forEach(this::startSubscriptions);

    subscriptionsToStop.forEach(this::stopSubscriptions);
  }

  public synchronized void stopGossip() {
    // Stop all active gossips
    activeSubscriptions.forEach(GossipForkSubscriptions::stopGossip);
    activeSubscriptions.clear();
    // Ensure we will create new active subscriptions if we are started again in the same epoch
    currentEpoch = Optional.empty();
  }

  public synchronized void onOptimisticHeadChanged(final boolean isHeadOptimistic) {
    if (isHeadOptimistic) {
      activeSubscriptions.forEach(GossipForkSubscriptions::stopGossipForOptimisticSync);
    } else {
      activeSubscriptions.forEach(
          subscriptions ->
              subscriptions.startGossip(
                  recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot(),
                  false));
    }
  }

  public synchronized void publishAttestation(final ValidateableAttestation attestation) {
    publishMessage(
        attestation.getData().getSlot(),
        attestation,
        "attestation",
        GossipForkSubscriptions::publishAttestation);
  }

  public synchronized void publishBlock(final SignedBeaconBlock block) {
    publishMessage(block.getSlot(), block, "block", GossipForkSubscriptions::publishBlock);
  }

  public synchronized void publishSyncCommitteeMessage(
      final ValidateableSyncCommitteeMessage message) {
    publishMessage(
        message.getSlot(),
        message,
        "sync committee message",
        GossipForkSubscriptions::publishSyncCommitteeMessage);
  }

  public void publishSyncCommitteeContribution(final SignedContributionAndProof message) {
    publishMessage(
        message.getMessage().getContribution().getSlot(),
        message,
        "sync committee contribution",
        GossipForkSubscriptions::publishSyncCommitteeContribution);
  }

  public void publishProposerSlashing(final ProposerSlashing message) {
    publishMessage(
        message.getHeader1().getMessage().getSlot(),
        message,
        "proposer slashing",
        GossipForkSubscriptions::publishProposerSlashing);
  }

  public void publishAttesterSlashing(final AttesterSlashing message) {
    publishMessage(
        message.getAttestation1().getData().getSlot(),
        message,
        "attester slashing",
        GossipForkSubscriptions::publishAttesterSlashing);
  }

  public void publishVoluntaryExit(final SignedVoluntaryExit message) {
    publishMessage(
        spec.computeStartSlotAtEpoch(message.getMessage().getEpoch()),
        message,
        "voluntary exit",
        GossipForkSubscriptions::publishVoluntaryExit);
  }

  public void publishSignedBlsToExecutionChanges(final SignedBlsToExecutionChange message) {
    publishMessage(
        spec.computeStartSlotAtEpoch(currentEpoch.orElseThrow()),
        message,
        "signed bls to execution change",
        GossipForkSubscriptions::publishSignedBlsToExecutionChangeMessage);
  }

  private <T> void publishMessage(
      final UInt64 slot,
      final T message,
      final String type,
      final BiConsumer<GossipForkSubscriptions, T> publisher) {
    getSubscriptionActiveAtSlot(slot)
        .filter(this::isActive)
        .ifPresentOrElse(
            subscription -> publisher.accept(subscription, message),
            () ->
                LOG.warn(
                    "Not publishing {} because no gossip subscriptions are active for slot {}",
                    type,
                    slot));
  }

  public synchronized void subscribeToAttestationSubnetId(final int subnetId) {
    if (currentAttestationSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToAttestationSubnetId(subnetId));
    }
  }

  public void unsubscribeFromAttestationSubnetId(final int subnetId) {
    if (currentAttestationSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromAttestationSubnetId(subnetId));
    }
  }

  public void subscribeToSyncCommitteeSubnetId(final int subnetId) {
    if (currentSyncCommitteeSubnets.add(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.subscribeToSyncCommitteeSubnet(subnetId));
    }
  }

  public void unsubscribeFromSyncCommitteeSubnetId(final int subnetId) {
    if (currentSyncCommitteeSubnets.remove(subnetId)) {
      activeSubscriptions.forEach(
          subscription -> subscription.unsubscribeFromSyncCommitteeSubnet(subnetId));
    }
  }

  private boolean isActive(final GossipForkSubscriptions subscriptions) {
    return activeSubscriptions.contains(subscriptions);
  }

  private void startSubscriptions(final GossipForkSubscriptions subscription) {
    if (activeSubscriptions.add(subscription)) {
      subscription.startGossip(
          recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot(),
          recentChainData.isChainHeadOptimistic());
      currentAttestationSubnets.forEach(subscription::subscribeToAttestationSubnetId);
      currentSyncCommitteeSubnets.forEach(subscription::subscribeToSyncCommitteeSubnet);
    }
  }

  private void stopSubscriptions(final GossipForkSubscriptions subscriptions) {
    if (activeSubscriptions.remove(subscriptions)) {
      subscriptions.stopGossip();
    }
  }

  private Optional<GossipForkSubscriptions> getSubscriptionActiveAtSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    return getSubscriptionActiveAtEpoch(epoch);
  }

  private Optional<GossipForkSubscriptions> getSubscriptionActiveAtEpoch(final UInt64 epoch) {
    return Optional.ofNullable(forksByActivationEpoch.floorEntry(epoch)).map(Map.Entry::getValue);
  }

  public static class Builder {

    private Spec spec;
    private RecentChainData recentChainData;
    private final NavigableMap<UInt64, GossipForkSubscriptions> forksByActivationEpoch =
        new TreeMap<>();

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder recentChainData(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      return this;
    }

    public Builder fork(final GossipForkSubscriptions forkSubscriptions) {
      final UInt64 activationEpoch = forkSubscriptions.getActivationEpoch();
      checkState(
          !forksByActivationEpoch.containsKey(activationEpoch),
          "Can not schedule two forks to activate at the same epoch");
      forksByActivationEpoch.put(activationEpoch, forkSubscriptions);
      return this;
    }

    public GossipForkManager build() {
      checkNotNull(spec, "Must supply spec");
      checkNotNull(recentChainData, "Must supply recentChainData");
      checkState(!forksByActivationEpoch.isEmpty(), "Must specify at least one fork");
      return new GossipForkManager(spec, recentChainData, forksByActivationEpoch);
    }
  }
}
