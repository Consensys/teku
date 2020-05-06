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

package tech.pegasys.teku.validator.client;

import static java.lang.Integer.min;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION;
import static tech.pegasys.teku.util.config.Constants.RANDOM_SUBNETS_PER_VALIDATOR;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.validator.api.SubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class StableSubnetSubscriber {

  private final ValidatorApiChannel validatorApiChannel;
  private final Set<Integer> availableSubnetIndices =
      IntStream.range(0, ATTESTATION_SUBNET_COUNT).boxed().collect(Collectors.toSet());
  private final NavigableSet<SubnetSubscription> subnetSubscriptions =
      new TreeSet<>(Comparator.comparing(SubnetSubscription::getUnsubscriptionSlot));
  private final Random rand = new Random();

  private volatile int validatorCount;

  public StableSubnetSubscriber(ValidatorApiChannel validatorApiChannel, int validatorCount) {
    this.validatorApiChannel = validatorApiChannel;
    this.validatorCount = validatorCount;
    onSlot(UnsignedLong.ZERO);
  }

  public void onSlot(UnsignedLong slot) {
    boolean updated = adjustNumberOfSubscriptionsToNumberOfValidators(slot, validatorCount);

    // Iterate through current subscriptions to replace the ones that have expired
    final Iterator<SubnetSubscription> iterator = subnetSubscriptions.iterator();
    while (iterator.hasNext()) {
      final SubnetSubscription subnetSubscription = iterator.next();
      if (subnetSubscription.getUnsubscriptionSlot().compareTo(slot) > 0) {
        break;
      }

      iterator.remove();
      int subnetId = subnetSubscription.getSubnetId();
      availableSubnetIndices.add(subnetId);
      subscribeToNewRandomSubnet(slot);
      updated = true;
    }

    // If any update was made to the subscriptions pass the new subscription set to BeaconNode
    if (updated) {
      validatorApiChannel.updatePersistentSubnetSubscriptions(subnetSubscriptions);
    }
  }

  public void updateValidatorCount(final int validatorCount) {
    this.validatorCount = validatorCount;
  }

  /**
   * Adjusts the number of subscriptions to the number of validators. Returns true if there was any
   * change made to the number of subscribed subnets.
   */
  private boolean adjustNumberOfSubscriptionsToNumberOfValidators(
      UnsignedLong currentSlot, int validatorCount) {
    boolean updated = false;

    int totalNumberOfSubscriptions =
        min(ATTESTATION_SUBNET_COUNT, RANDOM_SUBNETS_PER_VALIDATOR * validatorCount);

    while (subnetSubscriptions.size() != totalNumberOfSubscriptions) {
      if (subnetSubscriptions.size() < totalNumberOfSubscriptions) {
        subscribeToNewRandomSubnet(currentSlot);
      } else {
        unsubscribeFromRandomSubnet();
      }
      updated = true;
    }
    return updated;
  }

  /**
   * Subscribes to a new random subnetId, if any subnetID is available.
   *
   * @param currentSlot
   */
  private void subscribeToNewRandomSubnet(UnsignedLong currentSlot) {
    int newSubnetId =
        getRandomAvailableSubnetId()
            .orElseThrow(() -> new IllegalStateException("No available subnetId found"));

    availableSubnetIndices.remove(newSubnetId);
    subnetSubscriptions.add(
        new SubnetSubscription(newSubnetId, getRandomUnsubscriptionSlot(currentSlot)));
  }

  /**
   * Unsubscribe from a random subnet
   *
   * @return
   */
  private void unsubscribeFromRandomSubnet() {
    SubnetSubscription subnetSubscription =
        getRandomSetElement(subnetSubscriptions)
            .orElseThrow(() -> new IllegalStateException("No subnetId found to unsubscribe from."));

    subnetSubscriptions.remove(subnetSubscription);
    availableSubnetIndices.add(subnetSubscription.getSubnetId());
  }

  private Optional<Integer> getRandomAvailableSubnetId() {
    return getRandomSetElement(availableSubnetIndices);
  }

  private <T> Optional<T> getRandomSetElement(Set<T> set) {
    return set.stream().skip(rand.nextInt(set.size())).findFirst();
  }

  private UnsignedLong getRandomUnsubscriptionSlot(UnsignedLong currentSlot) {
    return currentSlot.plus(UnsignedLong.valueOf(getRandomSubscriptionLength()));
  }

  private int getRandomSubscriptionLength() {
    return EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION
        + rand.nextInt(EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION);
  }
}
