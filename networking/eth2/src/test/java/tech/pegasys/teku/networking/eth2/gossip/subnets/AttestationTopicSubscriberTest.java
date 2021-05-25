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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

class AttestationTopicSubscriberTest {

  private static final UInt64 COMMITTEES_AT_SLOT = UInt64.valueOf(20);
  private final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);

  private final Spec spec = TestSpecFactory.createDefault();
  private final AttestationTopicSubscriber subscriber =
      new AttestationTopicSubscriber(spec, eth2P2PNetwork);

  @Test
  public void shouldSubscribeToSubnet() {
    final int committeeId = 10;
    final int subnetId =
        spec.computeSubnetForCommittee(ONE, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);
    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, ONE);

    verify(eth2P2PNetwork).subscribeToAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldUnsubscribeFromSubnetWhenPastSlot() {
    final int committeeId = 12;
    final UInt64 aggregationSlot = UInt64.valueOf(10);
    final int subnetId =
        spec.computeSubnetForCommittee(
            aggregationSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);

    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, aggregationSlot);
    subscriber.onSlot(aggregationSlot.plus(ONE));

    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldNotUnsubscribeAtStartOfTargetSlot() {
    final int committeeId = 16;
    final UInt64 aggregationSlot = UInt64.valueOf(10);
    final int subnetId =
        spec.computeSubnetForCommittee(ONE, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);

    subscriber.subscribeToCommitteeForAggregation(subnetId, COMMITTEES_AT_SLOT, aggregationSlot);
    subscriber.onSlot(aggregationSlot);

    verify(eth2P2PNetwork, never()).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldExtendSubscriptionPeriod() {
    final int committeeId = 3;
    final UInt64 firstSlot = UInt64.valueOf(10);
    final UInt64 secondSlot = UInt64.valueOf(18);
    final int subnetId =
        spec.computeSubnetForCommittee(firstSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);
    // Sanity check second subscription is for the same subnet ID.
    assertThat(subnetId)
        .isEqualTo(
            spec.computeSubnetForCommittee(
                secondSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT));

    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, firstSlot);
    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, secondSlot);

    subscriber.onSlot(firstSlot.plus(ONE));
    verify(eth2P2PNetwork, never()).unsubscribeFromAttestationSubnetId(anyInt());

    subscriber.onSlot(secondSlot.plus(ONE));
    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldPreserveLaterSubscriptionPeriodWhenEarlierSlotAdded() {
    final int committeeId = 3;
    final UInt64 firstSlot = UInt64.valueOf(10);
    final UInt64 secondSlot = UInt64.valueOf(18);
    final int subnetId =
        spec.computeSubnetForCommittee(firstSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);
    // Sanity check the two subscriptions are for the same subnet
    assertThat(
            spec.computeSubnetForCommittee(
                secondSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT))
        .isEqualTo(subnetId);

    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, secondSlot);
    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, firstSlot);

    subscriber.onSlot(firstSlot.plus(ONE));
    verify(eth2P2PNetwork, never()).unsubscribeFromAttestationSubnetId(anyInt());

    subscriber.onSlot(secondSlot.plus(ONE));
    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldSubscribeToNewSubnetsAndUpdateENR_forPersistentSubscriptions() {
    Set<SubnetSubscription> subnetSubscriptions =
        Set.of(
            new SubnetSubscription(1, UInt64.valueOf(20)),
            new SubnetSubscription(2, UInt64.valueOf(15)));

    subscriber.subscribeToPersistentSubnets(subnetSubscriptions);

    verify(eth2P2PNetwork).setLongTermAttestationSubnetSubscriptions(Set.of(1, 2));

    verify(eth2P2PNetwork).subscribeToAttestationSubnetId(1);
    verify(eth2P2PNetwork).subscribeToAttestationSubnetId(2);
  }

  @Test
  public void shouldUpdateENRWhenNewSubnetIsSubscribedDueToPersistentSubscriptions() {
    UInt64 someSlot = UInt64.valueOf(15);
    Set<SubnetSubscription> subnetSubscription = Set.of(new SubnetSubscription(2, someSlot));

    subscriber.subscribeToCommitteeForAggregation(1, COMMITTEES_AT_SLOT, someSlot);
    subscriber.subscribeToCommitteeForAggregation(2, COMMITTEES_AT_SLOT, someSlot);

    verify(eth2P2PNetwork)
        .subscribeToAttestationSubnetId(
            spec.computeSubnetForCommittee(someSlot, UInt64.valueOf(1), COMMITTEES_AT_SLOT));
    verify(eth2P2PNetwork)
        .subscribeToAttestationSubnetId(
            spec.computeSubnetForCommittee(someSlot, UInt64.valueOf(2), COMMITTEES_AT_SLOT));

    subscriber.subscribeToPersistentSubnets(subnetSubscription);

    verify(eth2P2PNetwork).setLongTermAttestationSubnetSubscriptions(Set.of(2));

    verify(eth2P2PNetwork).subscribeToAttestationSubnetId(2);
  }

  @Test
  public void shouldExtendSubscription_forPersistentSubscriptions() {
    final int subnetId = 3;
    final UInt64 firstSlot = UInt64.valueOf(10);
    final UInt64 secondSlot = UInt64.valueOf(15);
    Set<SubnetSubscription> subnetSubscriptions =
        Set.of(new SubnetSubscription(subnetId, secondSlot));

    subscriber.subscribeToCommitteeForAggregation(subnetId, COMMITTEES_AT_SLOT, firstSlot);
    subscriber.subscribeToPersistentSubnets(subnetSubscriptions);

    subscriber.onSlot(firstSlot.plus(ONE));
    verify(eth2P2PNetwork, never()).unsubscribeFromAttestationSubnetId(subnetId);

    subscriber.onSlot(secondSlot.plus(ONE));
    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldPreserveLaterSubscription_forPersistentSubscriptions() {
    final int committeeId = 3;
    final UInt64 firstSlot = UInt64.valueOf(10);
    final UInt64 secondSlot = UInt64.valueOf(15);
    final int subnetId =
        spec.computeSubnetForCommittee(secondSlot, UInt64.valueOf(committeeId), COMMITTEES_AT_SLOT);
    subscriber.subscribeToCommitteeForAggregation(committeeId, COMMITTEES_AT_SLOT, secondSlot);
    Set<SubnetSubscription> subnetSubscriptions =
        Set.of(new SubnetSubscription(subnetId, firstSlot));
    subscriber.subscribeToPersistentSubnets(subnetSubscriptions);

    subscriber.onSlot(firstSlot.plus(ONE));
    verify(eth2P2PNetwork, never()).unsubscribeFromAttestationSubnetId(subnetId);

    subscriber.onSlot(secondSlot.plus(ONE));
    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Test
  public void shouldExtendSubscription_forSameSubnetWithDifferentUnsubscribeSlot() {
    final int subnetId = 3;
    final UInt64 firstSlot = UInt64.valueOf(10);
    final UInt64 secondSlot = UInt64.valueOf(15);

    Set<SubnetSubscription> subnetSubscriptions1 =
        Set.of(new SubnetSubscription(subnetId, firstSlot));
    subscriber.subscribeToPersistentSubnets(subnetSubscriptions1);

    verify(eth2P2PNetwork).subscribeToAttestationSubnetId(subnetId);
    verify(eth2P2PNetwork).setLongTermAttestationSubnetSubscriptions(Set.of(subnetId));

    Set<SubnetSubscription> subnetSubscriptions2 =
        Set.of(new SubnetSubscription(subnetId, secondSlot));
    subscriber.subscribeToPersistentSubnets(subnetSubscriptions2);

    verifyNoMoreInteractions(eth2P2PNetwork);

    subscriber.onSlot(secondSlot.plus(ONE));

    verify(eth2P2PNetwork).unsubscribeFromAttestationSubnetId(subnetId);
    verify(eth2P2PNetwork, times(2))
        .setLongTermAttestationSubnetSubscriptions(Collections.emptySet());
  }
}
