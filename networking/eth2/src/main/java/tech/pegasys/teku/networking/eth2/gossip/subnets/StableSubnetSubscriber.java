/*
 * Copyright 2020 ConsenSys AG.
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

import static java.lang.Integer.min;
import static java.util.Collections.emptySet;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION;
import static tech.pegasys.teku.util.config.Constants.RANDOM_SUBNETS_PER_VALIDATOR;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StableSubnetSubscriber {
  private static final Logger LOG = LogManager.getLogger();

  private final AttestationTopicSubscriber persistentSubnetSubscriber;
  private final Set<Integer> availableSubnetIndices = new HashSet<>();
  private final NavigableSet<SubnetSubscription> subnetSubscriptions =
      new TreeSet<>(
          Comparator.comparing(SubnetSubscription::getUnsubscriptionSlot)
              .thenComparing(SubnetSubscription::getSubnetId));
  private final Random random;

  private volatile int validatorCount = 0;

  public StableSubnetSubscriber(
      final AttestationTopicSubscriber persistentSubnetSubscriber, final Random random) {
    this.persistentSubnetSubscriber = persistentSubnetSubscriber;
    this.random = random;
    IntStream.range(0, ATTESTATION_SUBNET_COUNT).forEach(availableSubnetIndices::add);
  }

  public void onSlot(UInt64 slot) {
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
        adjustNumberOfSubscriptionsToNumberOfValidators(slot, validatorCount);
    if (!newSubnetSubscriptions.isEmpty()) {
      persistentSubnetSubscriber.subscribeToPersistentSubnets(newSubnetSubscriptions);
    }
  }

  public void updateValidatorCount(final int validatorCount) {
    this.validatorCount = validatorCount;
  }

  /**
   * Adjusts the number of subscriptions to the number of validators. Returns the set of new
   * subscriptions that were added, if there were no new subscriptions, or if there were
   * unsubscriptions, it returns an empty set.
   */
  private Set<SubnetSubscription> adjustNumberOfSubscriptionsToNumberOfValidators(
      UInt64 currentSlot, int validatorCount) {

    int totalNumberOfSubscriptions =
        min(ATTESTATION_SUBNET_COUNT, RANDOM_SUBNETS_PER_VALIDATOR * validatorCount);

    if (subnetSubscriptions.size() == totalNumberOfSubscriptions) {
      return emptySet();
    }
    LOG.info(
        "Updating number of persistent subnet subscriptions from {} to {}",
        subnetSubscriptions.size(),
        totalNumberOfSubscriptions);
    Set<SubnetSubscription> newSubnetSubscriptions = new HashSet<>();

    while (subnetSubscriptions.size() != totalNumberOfSubscriptions) {
      if (subnetSubscriptions.size() < totalNumberOfSubscriptions) {
        newSubnetSubscriptions.add(subscribeToNewRandomSubnet(currentSlot));
      } else {
        unsubscribeFromRandomSubnet();
      }
    }
    return newSubnetSubscriptions;
  }

  /**
   * Subscribes to a new random subnetId, if any subnetID is available. Returns the new
   * SubnetSubscription object.
   *
   * @param currentSlot the current slot
   */
  private SubnetSubscription subscribeToNewRandomSubnet(UInt64 currentSlot) {
    int newSubnetId =
        getRandomAvailableSubnetId()
            .orElseThrow(() -> new IllegalStateException("No available subnetId found"));

    availableSubnetIndices.remove(newSubnetId);
    SubnetSubscription subnetSubscription =
        new SubnetSubscription(newSubnetId, getRandomUnsubscriptionSlot(currentSlot));
    subnetSubscriptions.add(subnetSubscription);
    return subnetSubscription;
  }

  /** Unsubscribe from a random subnet */
  private void unsubscribeFromRandomSubnet() {
    SubnetSubscription subnetSubscription =
        getRandomSetElement(subnetSubscriptions)
            .orElseThrow(
                () ->
                    new IllegalStateException("No subnet subscription found to unsubscribe from."));

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
    return currentSlot.plus(getRandomSubscriptionLength());
  }

  private UInt64 getRandomSubscriptionLength() {
    return UInt64.valueOf(
        (EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION
                + random.nextInt(EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION))
            * SLOTS_PER_EPOCH);
  }
}
