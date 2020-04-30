package tech.pegasys.artemis.validator.client;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.max;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION;
import static tech.pegasys.artemis.util.config.Constants.RANDOM_SUBNETS_PER_VALIDATOR;

public class StableSubnetSubscriber implements ValidatorTimingChannel {

  private final ValidatorApiChannel validatorApiChannel;
  private final Map<BLSPublicKey, Validator> validators;
  private final Set<Integer> availableSubnetIndices =
          IntStream.range(0, ATTESTATION_SUBNET_COUNT).boxed().collect(Collectors.toSet());
  private final Map<Integer, UnsignedLong> subnetIdToUnsubscriptionSlot = new HashMap<>();
  private final Random rand = new Random();

  public StableSubnetSubscriber(ValidatorApiChannel validatorApiChannel,
                                Map<BLSPublicKey, Validator> validators) {
    this.validatorApiChannel = validatorApiChannel;
    this.validators = validators;
  }

  @Override
  public void onSlot(UnsignedLong slot) {
    boolean updated = adjustNumberOfSubscriptionsToNumberOfValidators(slot);

    final Iterator<Map.Entry<Integer, UnsignedLong>> iterator =
            subnetIdToUnsubscriptionSlot.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<Integer, UnsignedLong> entry = iterator.next();
      if (entry.getValue().compareTo(slot) > 0) {
        continue;
      }

      iterator.remove();
      int subnetId = entry.getKey();
      availableSubnetIndices.add(subnetId);
      subscribeToNewRandomSubnet(slot);
      updated = true;
    }

    if (updated) {
      validatorApiChannel.updateRandomSubnetSubscriptions(subnetIdToUnsubscriptionSlot);
    }
  }

  /**
   * Adjusts the number of subscriptions to the number of validators.
   * Returns true if there was any change made to the number of subscribed subnets.
   */
  private boolean adjustNumberOfSubscriptionsToNumberOfValidators(UnsignedLong currentSlot) {
    boolean updated = false;

    int totalNumberOfSubscriptions = max(
            ATTESTATION_SUBNET_COUNT,
            RANDOM_SUBNETS_PER_VALIDATOR * validators.size()
    );

    while(subnetIdToUnsubscriptionSlot.size() != totalNumberOfSubscriptions) {
      if (subnetIdToUnsubscriptionSlot.size() < totalNumberOfSubscriptions) {
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
    int newSubnetId = getRandomAvailableSubnetId()
            .orElseThrow(() -> new IllegalStateException("No available subnetId found"));

    availableSubnetIndices.remove(newSubnetId);
    subnetIdToUnsubscriptionSlot.put(
            newSubnetId,
            getRandomUnsubscriptionSlot(currentSlot)
    );
  }

  /**
   * Unsubscribe from a random subnet
   * @return
   */
  private void unsubscribeFromRandomSubnet() {
    int subnetId = getRandomSetElement(subnetIdToUnsubscriptionSlot.keySet())
            .orElseThrow(() -> new IllegalStateException("No subnetId found to unsubscribe from."));
    subnetIdToUnsubscriptionSlot.remove(subnetId);
    availableSubnetIndices.add(subnetId);
  }

  private Optional<Integer> getRandomAvailableSubnetId() {
    return getRandomSetElement(availableSubnetIndices);
  }

  private <T> Optional<T> getRandomSetElement(Set<T> set) {
    return set
            .stream()
            .skip(rand.nextInt(set.size()))
            .findFirst();
  }

  private UnsignedLong getRandomUnsubscriptionSlot(UnsignedLong currentSlot) {
    return currentSlot.plus(UnsignedLong.valueOf(getRandomSubscriptionLength()));
  }

  private int getRandomSubscriptionLength() {
    return EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION + rand.nextInt(EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION);
  }

  @Override
  public void onChainReorg(UnsignedLong newSlot) {}

  @Override
  public void onBlockProductionDue(UnsignedLong slot) {}

  @Override
  public void onAttestationCreationDue(UnsignedLong slot) {}

  @Override
  public void onAttestationAggregationDue(UnsignedLong slot) {}
}
