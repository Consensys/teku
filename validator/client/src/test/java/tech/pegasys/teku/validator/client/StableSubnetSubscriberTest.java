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
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));
  }

  @Test
  void shouldLowerNumberOfSubscriptionsWhenNumberOfValidatorsDecrease() {
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(1);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 1));
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

    stableSubnetSubscriber.updateValidatorCount(3);

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(argThat(arg -> arg.size() == 3));
  }

  @Test
  void shouldSubscribeToAllSubnetsWhenNecessary() {
    // Attestation Subnet Count is 64
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

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
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

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
    verify(validatorApiChannel).subscribeToPersistentSubnets(argThat(arg -> arg.size() == 2));

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

    // set random subscription length to 10
    Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 5;
    Constants.SLOTS_PER_EPOCH = 2;
    when(mockRandom.nextInt(Constants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION)).thenReturn(0);

    // return subnet Ids 0 and 1
    when(mockRandom.nextInt(Constants.ATTESTATION_SUBNET_COUNT)).thenReturn(0);
    when(mockRandom.nextInt(Constants.ATTESTATION_SUBNET_COUNT - 1)).thenReturn(0);
    when(mockRandom.nextInt(Constants.ATTESTATION_SUBNET_COUNT - 2)).thenReturn(0);
    when(mockRandom.nextInt(Constants.ATTESTATION_SUBNET_COUNT - 3)).thenReturn(0);

    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, mockRandom, 2);

    stableSubnetSubscriber.onSlot(valueOf(0));

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(validatorApiChannel).subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(2);
    assertThat(firstSubscriptionUpdate.getValue())
        .containsExactlyInAnyOrder(
            new SubnetSubscription(0, valueOf(10)), new SubnetSubscription(1, valueOf(10)));

    stableSubnetSubscriber.onSlot(valueOf(9));

    verifyNoMoreInteractions(validatorApiChannel);

    stableSubnetSubscriber.onSlot(valueOf(10));

    verify(validatorApiChannel, times(2))
        .subscribeToPersistentSubnets(secondSubscriptionUpdate.capture());

    assertThat(secondSubscriptionUpdate.getValue()).hasSize(2);
    assertThat(secondSubscriptionUpdate.getValue())
        .containsExactlyInAnyOrder(
            new SubnetSubscription(0, valueOf(20)), new SubnetSubscription(1, valueOf(20)));
  }
}
