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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

@SuppressWarnings("unchecked")
public class StableSubnetSubscriberTest {
  private final AttestationTopicSubscriber validatorApiChannel =
      mock(AttestationTopicSubscriber.class);

  @BeforeEach
  void setUp() {
    Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 5;
  }

  @AfterEach
  void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber(2);

    stableSubnetSubscriber.onSlot(ZERO);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(2);
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
  }

  @Test
  void shouldNotNotifyAnyChangeWhenNumberOfValidatorsDecrease() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber(2);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    stableSubnetSubscriber.onSlot(ZERO);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());

    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());

    stableSubnetSubscriber.updateValidatorCount(1);

    stableSubnetSubscriber.onSlot(UInt64.ONE);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber(0);

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
    StableSubnetSubscriber stableSubnetSubscriber =
        createStableSubnetSubscriber(Constants.ATTESTATION_SUBNET_COUNT + 2);

    UInt64 slot = UInt64.valueOf(15);
    stableSubnetSubscriber.onSlot(slot);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), UInt64.valueOf(15));
  }

  @Test
  void shouldStaySubscribedToAllSubnetsEvenIfValidatorNumberIsDecreased() {
    StableSubnetSubscriber stableSubnetSubscriber =
        createStableSubnetSubscriber(Constants.ATTESTATION_SUBNET_COUNT + 8);

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);

    stableSubnetSubscriber.updateValidatorCount(Constants.ATTESTATION_SUBNET_COUNT);
    stableSubnetSubscriber.onSlot(UInt64.valueOf(2));

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber(1);

    stableSubnetSubscriber.onSlot(UInt64.valueOf(0));

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(1);

    UInt64 firstUnsubscriptionSlot =
        firstSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UInt64.ONE));

    verifyNoMoreInteractions(validatorApiChannel);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(secondSubscriptionUpdate.capture());

    UInt64 secondUnsubscriptionSlot =
        secondSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    assertThat(firstUnsubscriptionSlot).isNotEqualByComparingTo(secondUnsubscriptionSlot);
    // Can only verify unsubscription slot have changed and not the subnet id,
    // since subnet id can randomly be chosen the same
  }

  @Test
  void shouldGenerateLargeNumberOfSubscriptionsAndCheckTheyreAllCorrect() {
    StableSubnetSubscriber stableSubnetSubscriber =
        createStableSubnetSubscriber(Constants.ATTESTATION_SUBNET_COUNT);

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);
  }

  private void assertUnsubscribeSlotsAreInBound(
      Set<SubnetSubscription> subnetSubscriptions, UInt64 currentSlot) {
    UInt64 lowerBound =
        currentSlot.plus(
            UInt64.valueOf(
                Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION * Constants.SLOTS_PER_EPOCH));
    UInt64 upperBound =
        currentSlot.plus(
            UInt64.valueOf(
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

  private StableSubnetSubscriber createStableSubnetSubscriber(final int validatorCount) {
    final StableSubnetSubscriber subscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(13241234L));
    subscriber.updateValidatorCount(validatorCount);
    return subscriber;
  }
}
