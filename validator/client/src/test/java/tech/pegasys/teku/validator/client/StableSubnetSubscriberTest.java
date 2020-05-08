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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.primitives.UnsignedLong;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@SuppressWarnings("unchecked")
public class StableSubnetSubscriberTest {
  @BeforeEach
  void setUp() {
    Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 5;
  }

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), 2);

    stableSubnetSubscriber.onSlot(ZERO);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(2);
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
  }

  @Test
  void shouldNotNotifyAnyChangeWhenNumberOfValidatorsDecrease() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), 2);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    stableSubnetSubscriber.onSlot(ZERO);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());

    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());

    stableSubnetSubscriber.updateValidatorCount(1);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), 0);

    stableSubnetSubscriber.onSlot(ZERO);
    verifyNoInteractions(validatorApiChannel);

    stableSubnetSubscriber.updateValidatorCount(3);

    stableSubnetSubscriber.onSlot(ONE);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());

    assertThat(subnetSubcriptions.getValue()).hasSize(3);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
  }

  @Test
  void shouldSubscribeToAllSubnetsWhenNecessary() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(
            validatorApiChannel, new Random(), Constants.ATTESTATION_SUBNET_COUNT + 2);

    UnsignedLong slot = valueOf(15);
    stableSubnetSubscriber.onSlot(slot);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), valueOf(15));
  }

  @Test
  void shouldStaySubscribedToAllSubnetsEvenIfValidatorNumberIsDecreased() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(
            validatorApiChannel, new Random(), Constants.ATTESTATION_SUBNET_COUNT + 8);

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);

    stableSubnetSubscriber.updateValidatorCount(Constants.ATTESTATION_SUBNET_COUNT);
    stableSubnetSubscriber.onSlot(valueOf(2));

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), 2);

    stableSubnetSubscriber.onSlot(valueOf(0));

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(2);
    assertThat(firstSubscriptionUpdate.getValue()).hasSize(2);

    UnsignedLong firstUnsubscriptionSlot =
        firstSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UnsignedLong.ONE));

    verifyNoMoreInteractions(validatorApiChannel);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(secondSubscriptionUpdate.capture());

    UnsignedLong secondUnsubscriptionSlot =
        secondSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    assertThat(firstUnsubscriptionSlot).isNotEqualByComparingTo(secondUnsubscriptionSlot);
    // Can only verify unsubscription slot have changed and not the subnet id,
    // since subnet id can randomly be chosen the same
  }

  private void assertUnsubscribeSlotsAreInBound(
      Set<SubnetSubscription> subnetSubscriptions, UnsignedLong currentSlot) {
    UnsignedLong lowerBound =
        currentSlot.plus(
            valueOf(Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION * Constants.SLOTS_PER_EPOCH));
    UnsignedLong upperBound =
        currentSlot.plus(
            valueOf(
                2 * Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION * Constants.SLOTS_PER_EPOCH));
    subnetSubscriptions.forEach(
        subnetSubscription -> {
          assertThat(subnetSubscription.getUnsubscriptionSlot()).isBetween(lowerBound, upperBound);
        });
  }

  private void assertSubnetsAreDistinct(Set<SubnetSubscription> subnetSubscriptions) {
    Set<Integer> subnetIds =
        subnetSubscriptions.stream()
            .map(SubnetSubscription::getSubnetId)
            .collect(Collectors.toSet());
    assertThat(subnetSubscriptions).hasSameSizeAs(subnetIds);
  }
}
