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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static java.util.Collections.emptySet;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public class NodeBasedStableSubnetSubscriber implements StableSubnetSubscriber {
  private static final Logger LOG = LogManager.getLogger();

  private final AttestationTopicSubscriber persistentSubnetSubscriber;
  private final IntSet availableSubnetIndices = new IntOpenHashSet();
  private final NavigableSet<SubnetSubscription> subnetSubscriptions =
      new TreeSet<>(
          Comparator.comparing(SubnetSubscription::getUnsubscriptionSlot)
              .thenComparing(SubnetSubscription::getSubnetId));
  private final Random random;
  private final Spec spec;
  private final int attestationSubnetCount;
  private final int subnetsPerNode;
  private final int epochsPerSubnetSubscription;
  private final Optional<UInt256> discoveryNodeId;

  public NodeBasedStableSubnetSubscriber(
      final AttestationTopicSubscriber persistentSubnetSubscriber,
      final Random random,
      final Spec spec,
      final Optional<UInt256> discoveryNodeId) {
    this.persistentSubnetSubscriber = persistentSubnetSubscriber;
    this.random = random;
    this.spec = spec;
    this.attestationSubnetCount = spec.getNetworkingConfig().getAttestationSubnetCount();
    this.subnetsPerNode = spec.getNetworkingConfig().getSubnetsPerNode();
    this.epochsPerSubnetSubscription = spec.getNetworkingConfig().getEpochsPerSubnetSubscription();
    IntStream.range(0, attestationSubnetCount).forEach(availableSubnetIndices::add);
    this.discoveryNodeId = discoveryNodeId;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // Iterate through current subscriptions to remove the ones that have expired
    final Iterator<SubnetSubscription> iterator = subnetSubscriptions.iterator();
    while (iterator.hasNext()) {
      final SubnetSubscription subnetSubscription = iterator.next();
      if (subnetSubscription.getUnsubscriptionSlot().compareTo(slot) > 0) {
        break;
      }

      iterator.remove();
      int subnetId = subnetSubscription.getSubnetId();
      availableSubnetIndices.add(subnetId);
    }

    // Adjust the number of subscriptions
    // If there are new subscriptions, pass the new subscription set to BeaconNode
    Set<SubnetSubscription> newSubnetSubscriptions =
        adjustNumberOfSubscriptionsToNodeRequirement(slot);
    if (!newSubnetSubscriptions.isEmpty()) {
      persistentSubnetSubscriber.subscribeToPersistentSubnets(newSubnetSubscriptions);
    }
  }

  /**
   * Adjusts the number of subscriptions to SUBNETS_PER_NODE. Returns the set of new subscriptions
   * that were added, if there were no new subscriptions, or if there were unsubscriptions only, it
   * returns an empty set.
   */
  private Set<SubnetSubscription> adjustNumberOfSubscriptionsToNodeRequirement(
      final UInt64 currentSlot) {

    if (subnetSubscriptions.size() == subnetsPerNode) {
      return emptySet();
    }
    LOG.info(
        "Updating number of persistent subnet subscriptions from {} to {}",
        subnetSubscriptions.size(),
        subnetsPerNode);

    final List<UInt64> nodeSubscribedSubnets =
        spec.getGenesisSpec()
            .miscHelpers()
            .computeSubscribedSubnets(
                discoveryNodeId.orElseThrow(
                    () -> new IllegalArgumentException("Unable to get discovery node id")),
                spec.computeEpochAtSlot(currentSlot));

    final Iterator<UInt64> nodeSubscribedSubnetsIterator = nodeSubscribedSubnets.iterator();

    final Set<SubnetSubscription> newSubnetSubscriptions = new HashSet<>();

    while (subnetSubscriptions.size() != subnetsPerNode) {
      if (subnetSubscriptions.size() < subnetsPerNode) {
        if (nodeSubscribedSubnetsIterator.hasNext()) {
          newSubnetSubscriptions.add(
              subscribeToSubnet(
                  nodeSubscribedSubnetsIterator.next().intValue(),
                  calculateNodeSubnetUnsubscriptionSlot(currentSlot)));
        } else {
          LOG.warn(
              "Unable to get enough subnet ids based on node id. Subscribing to a random subnet instead.");
          newSubnetSubscriptions.add(subscribeToNewRandomSubnet(currentSlot));
        }
      } else {
        unsubscribeFromOldestSubnet();
      }
    }
    return newSubnetSubscriptions;
  }

  private UInt64 calculateNodeSubnetUnsubscriptionSlot(final UInt64 currentSlot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    final UInt64 currentEpochStartSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 unsubscriptionEpoch =
        spec.computeEpochAtSlot(currentSlot).plus(epochsPerSubnetSubscription);
    return spec.computeStartSlotAtEpoch(unsubscriptionEpoch)
        .plus(currentSlot.minus(currentEpochStartSlot));
  }

  /**
   * Subscribes to a new random subnetId, if any subnetId is available. Returns the new
   * SubnetSubscription object.
   *
   * @param currentSlot the current slot
   */
  private SubnetSubscription subscribeToNewRandomSubnet(final UInt64 currentSlot) {
    int newSubnetId =
        getRandomAvailableSubnetId()
            .orElseThrow(() -> new IllegalStateException("No available subnetId found"));
    return subscribeToSubnet(newSubnetId, getRandomUnsubscriptionSlot(currentSlot));
  }

  private SubnetSubscription subscribeToSubnet(
      final int subnetId, final UInt64 unsubscriptionSlot) {
    availableSubnetIndices.remove(subnetId);
    SubnetSubscription subnetSubscription = new SubnetSubscription(subnetId, unsubscriptionSlot);
    subnetSubscriptions.add(subnetSubscription);
    return subnetSubscription;
  }

  private void unsubscribeFromOldestSubnet() {
    SubnetSubscription subnetSubscription = subnetSubscriptions.first();
    subnetSubscriptions.remove(subnetSubscription);
    availableSubnetIndices.add(subnetSubscription.getSubnetId());
  }

  private Optional<Integer> getRandomAvailableSubnetId() {
    return getRandomSetElement(availableSubnetIndices);
  }

  private <T> Optional<T> getRandomSetElement(Set<T> set) {
    return set.stream().skip(random.nextInt(set.size())).findFirst();
  }

  private UInt64 getRandomUnsubscriptionSlot(UInt64 currentSlot) {
    return currentSlot.plus(getRandomSubscriptionLength(currentSlot));
  }

  private UInt64 getRandomSubscriptionLength(final UInt64 currentSlot) {
    final SpecConfig config = spec.atSlot(currentSlot).getConfig();

    return UInt64.valueOf(
        (long)
                (config.getEpochsPerSubnetSubscription()
                    + random.nextInt(config.getEpochsPerSubnetSubscription()))
            * config.getSlotsPerEpoch());
  }
}
