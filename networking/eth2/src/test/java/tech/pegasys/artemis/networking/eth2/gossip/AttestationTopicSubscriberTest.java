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
import static org.mockito.Mockito.verify;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.Eth2Network;

class AttestationTopicSubscriberTest {

  private final Eth2Network eth2Network = mock(Eth2Network.class);

  private final AttestationTopicSubscriber subscriptions =
      new AttestationTopicSubscriber(eth2Network);

  @Test
  public void shouldSubscribeToCommittee() {
    final int committeeIndex = 10;
    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, ONE);

    verify(eth2Network).subscribeToAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldUnsubscribeFromCommitteeWhenPastSlot() {
    final int committeeIndex = 12;
    final UnsignedLong aggregationSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, aggregationSlot);

    subscriptions.onSlot(aggregationSlot.plus(ONE));

    verify(eth2Network).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldNotUnsubscribeAtStartOfTargetSlot() {
    final int committeeIndex = 16;
    final UnsignedLong aggregationSlot = UnsignedLong.valueOf(10);
    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, aggregationSlot);

    subscriptions.onSlot(aggregationSlot);

    verify(eth2Network, never()).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }

  @Test
  public void shouldExtendSubscriptionPeriod() {
    final int committeeIndex = 3;
    final UnsignedLong firstSlot = UnsignedLong.valueOf(10);
    final UnsignedLong secondSlot = UnsignedLong.valueOf(15);

    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, firstSlot);
    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, secondSlot);

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

    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, secondSlot);
    subscriptions.subscribeToCommitteeForAggregation(committeeIndex, firstSlot);

    subscriptions.onSlot(firstSlot.plus(ONE));
    verify(eth2Network, never()).unsubscribeFromAttestationCommitteeTopic(committeeIndex);

    subscriptions.onSlot(secondSlot.plus(ONE));
    verify(eth2Network).unsubscribeFromAttestationCommitteeTopic(committeeIndex);
  }
}
