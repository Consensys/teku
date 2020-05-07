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

import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.SubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class StableSubnetSubscriberTest {
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private StableSubnetSubscriber stableSubnetSubscriber;

  @BeforeEach
  void setUp() {
    stableSubnetSubscriber = new StableSubnetSubscriber(validatorApiChannel, new Random(), 2);
    stableSubnetSubscriber.onSlot(valueOf(0));
  }

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));
  }

  @Test
  void shouldLowerNumberOfSubscriptionsWhenNumberOfValidatorsDecrease() {
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(1);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 1));
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(3);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 3));
  }

  @Test
  void shouldSubscribeToAllSubnetsWhenNecessary() {
    // Attestation Subnet Count is 64
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    // with 66 validators, we'll have to subscribe to all subnets
    stableSubnetSubscriber.updateValidatorCount(66);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(
            argThat(arg -> arg.size() == Constants.ATTESTATION_SUBNET_COUNT));
  }

  @Test
  void shouldSubscribeToAllSubnetsEvenIfValidatorNumberIsDecreased() {
    // Attestation Subnet Count is 64
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(72);
    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(
            argThat(arg -> arg.size() == Constants.ATTESTATION_SUBNET_COUNT));

    stableSubnetSubscriber.updateValidatorCount(65);
    stableSubnetSubscriber.onSlot(valueOf(2));

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldUnsubscribeFromAllSubnetsWhenValidatorCountGoesToZero() {
    // Attestation Subnet Count is 64
    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(72);
    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(
            argThat(arg -> arg.size() == Constants.ATTESTATION_SUBNET_COUNT));

    stableSubnetSubscriber.updateValidatorCount(0);
    stableSubnetSubscriber.onSlot(valueOf(2));

    verify(validatorApiChannel, times(3))
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
    Random mockRandom = mock(Random.class);
    when(mockRandom.nextInt(2)).thenReturn(10);

    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), 2);

    stableSubnetSubscriber.onSlot(valueOf(0));

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel)
        .subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

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
}
