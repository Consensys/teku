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

package tech.pegasys.artemis.networking.eth2.gossip;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.ssz.SSZTypes.Bitvector;

class AttestationTopicSubscriptionsTest {

  private final Eth2Network eth2Network = mock(Eth2Network.class);

  private final AttestationTopicSubscriptions subscriptions =
      new AttestationTopicSubscriptions(eth2Network);

  @Test
  public void shouldSubscribeToCommittee() {
    final int committeeIndex = 10;
    subscriptions.subscribeToCommittee(committeeIndex, ONE);

    verify(eth2Network).subscribeToAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldUnsubscribeFromCommitteeWhenPastSlot() {
    final int committeeIndex = 12;
    final UnsignedLong aggregationSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommittee(committeeIndex, aggregationSlot);

    subscriptions.onSlot(aggregationSlot.plus(ONE));

    verify(eth2Network).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldNotUnsubscribeAtStartOfTargetSlot() {
    final int committeeIndex = 16;
    final UnsignedLong aggregationSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommittee(committeeIndex, aggregationSlot);

    subscriptions.onSlot(aggregationSlot);

    verify(eth2Network, never()).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldExtendSubscriptionPeriod() {
    final int committeeIndex = 3;
    final UnsignedLong firstSlot = UnsignedLong.valueOf(10);
    final UnsignedLong secondSlot = UnsignedLong.valueOf(15);

    subscriptions.subscribeToCommittee(committeeIndex, firstSlot);
    subscriptions.subscribeToCommittee(committeeIndex, secondSlot);

    subscriptions.onSlot(firstSlot.plus(ONE));
    verify(eth2Network, never()).unsubscribeFromAttestationCommitteeTopic(committeeIndex);

    subscriptions.onSlot(secondSlot.plus(ONE));
    verify(eth2Network).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldPreserveLaterSubscriptionPeriodWhenEarlierSlotAdded() {
    final int committeeIndex = 3;
    final UnsignedLong firstSlot = UnsignedLong.valueOf(10);
    final UnsignedLong secondSlot = UnsignedLong.valueOf(15);

    subscriptions.subscribeToCommittee(committeeIndex, secondSlot);

    subscriptions.onSlot(firstSlot.plus(ONE));
    verify(eth2Network, never()).unsubscribeFromAttestationCommitteeTopic(committeeIndex);

    subscriptions.onSlot(secondSlot.plus(ONE));
    verify(eth2Network).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldSubscribeToSubnet() {
    final int committeeIndex = 3;

    final UnsignedLong firstSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommittee(committeeIndex, firstSlot);
    int subnetIndex = committeeIndex % ATTESTATION_SUBNET_COUNT;
    Bitvector bitvector = new Bitvector(ATTESTATION_SUBNET_COUNT);
    bitvector.setBit(subnetIndex);

    verify(eth2Network).updateAttestationSubnetENRField(bitvector.serialize());
  }

  @Test
  public void shouldSubscribeToMultipleSubnets() {
    final int committeeIndex1 = 3;
    final int committeeIndex2 = 10;

    final UnsignedLong firstSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommittee(committeeIndex1, firstSlot);
    subscriptions.subscribeToCommittee(committeeIndex2, firstSlot);
    int subnetIndex1 = committeeIndex1 % ATTESTATION_SUBNET_COUNT;
    int subnetIndex2 = committeeIndex2 % ATTESTATION_SUBNET_COUNT;
    Bitvector bitvector = new Bitvector(ATTESTATION_SUBNET_COUNT);

    bitvector.setBit(subnetIndex1);
    verify(eth2Network).updateAttestationSubnetENRField(bitvector.serialize());

    bitvector.setBit(subnetIndex2);
    verify(eth2Network).updateAttestationSubnetENRField(bitvector.serialize());
  }

  @Test
  public void shouldNotUnsubscribeWhenThereAreOtherCommitteeThatMapToSameSubnet() {

    final int committeeIndex1 = 4;
    final int committeeIndex2 = 4 + ATTESTATION_SUBNET_COUNT;

    final UnsignedLong slot1 = UnsignedLong.valueOf(10);
    final UnsignedLong slot2 = UnsignedLong.valueOf(13);
    subscriptions.subscribeToCommittee(committeeIndex1, slot1);
    subscriptions.subscribeToCommittee(committeeIndex2, slot2);

    Bitvector bitvector = new Bitvector(ATTESTATION_SUBNET_COUNT);
    bitvector.setBit(4);

    verify(eth2Network, times(1)).updateAttestationSubnetENRField(bitvector.serialize());

    subscriptions.onSlot(slot1.plus(ONE));
    verify(eth2Network, times(1)).updateAttestationSubnetENRField(bitvector.serialize());

    bitvector.clearBit(4);
    subscriptions.onSlot(slot2.plus(ONE));
    verify(eth2Network).updateAttestationSubnetENRField(bitvector.serialize());
  }
}
