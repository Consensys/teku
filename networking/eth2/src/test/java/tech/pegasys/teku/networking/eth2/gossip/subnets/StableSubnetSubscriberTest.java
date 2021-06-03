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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.util.config.Constants;

@SuppressWarnings("unchecked")
public class StableSubnetSubscriberTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final AttestationTopicSubscriber validatorApiChannel =
      mock(AttestationTopicSubscriber.class);

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO, 2);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(2);
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
  }

  @Test
  void shouldNotNotifyAnyChangeWhenNumberOfValidatorsDecrease() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);

    stableSubnetSubscriber.onSlot(ZERO, 2);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());

    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());

    stableSubnetSubscriber.onSlot(UInt64.ONE, 1);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO, 0);
    verifyNoInteractions(validatorApiChannel);

    stableSubnetSubscriber.onSlot(ONE, 3);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());

    assertThat(subnetSubcriptions.getValue()).hasSize(3);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
  }

  @Test
  void shouldSubscribeToAllSubnetsWhenNecessary() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    UInt64 slot = UInt64.valueOf(15);
    stableSubnetSubscriber.onSlot(slot, Constants.ATTESTATION_SUBNET_COUNT + 2);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertUnsubscribeSlotsAreInBound(subnetSubcriptions.getValue(), UInt64.valueOf(15));
  }

  @Test
  void shouldStaySubscribedToAllSubnetsEvenIfValidatorNumberIsDecreased() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO, Constants.ATTESTATION_SUBNET_COUNT + 8);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubcriptions = ArgumentCaptor.forClass(Set.class);
    verify(validatorApiChannel).subscribeToPersistentSubnets(subnetSubcriptions.capture());
    assertSubnetsAreDistinct(subnetSubcriptions.getValue());
    assertThat(subnetSubcriptions.getValue()).hasSize(Constants.ATTESTATION_SUBNET_COUNT);

    stableSubnetSubscriber.onSlot(UInt64.valueOf(2), Constants.ATTESTATION_SUBNET_COUNT);

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(UInt64.valueOf(0), 1);

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(1);

    UInt64 firstUnsubscriptionSlot =
        firstSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UInt64.ONE), 1);

    verifyNoMoreInteractions(validatorApiChannel);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot, 1);

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
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO, Constants.ATTESTATION_SUBNET_COUNT);

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
                (long) ValidatorConstants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION
                    * spec.getGenesisSpecConfig().getSlotsPerEpoch()));
    UInt64 upperBound =
        currentSlot.plus(
            UInt64.valueOf(
                2L
                    * ValidatorConstants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION
                    * spec.getGenesisSpecConfig().getSlotsPerEpoch()));
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

  private StableSubnetSubscriber createStableSubnetSubscriber() {
    return new ValidatorBasedStableSubnetSubscriber(
        validatorApiChannel, new Random(13241234L), spec);
  }
}
