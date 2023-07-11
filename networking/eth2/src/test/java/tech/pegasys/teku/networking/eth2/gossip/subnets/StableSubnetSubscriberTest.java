/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("unchecked")
public class StableSubnetSubscriberTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);

  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void shouldSubscribeToSubnetsPerNodeAtStart() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubscriptions =
        ArgumentCaptor.forClass(Set.class);

    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(subnetSubscriptions.capture());
    assertThat(subnetSubscriptions.getValue())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());
    assertUnsubscribeSlotsAreInBound(subnetSubscriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubscriptions.getValue());
  }

  @Test
  void shouldNotNotifyAnyChangeWhenNumberOfValidatorsDecrease() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubscriptions =
        ArgumentCaptor.forClass(Set.class);

    stableSubnetSubscriber.onSlot(ZERO);
    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(subnetSubscriptions.capture());

    assertUnsubscribeSlotsAreInBound(subnetSubscriptions.getValue(), ZERO);
    assertSubnetsAreDistinct(subnetSubscriptions.getValue());

    stableSubnetSubscriber.onSlot(UInt64.ONE);
    verifyNoMoreInteractions(attestationTopicSubscriber);
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(attestationTopicSubscriber)
        .subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());

    UInt64 firstUnsubscriptionSlot =
        firstSubscriptionUpdate.getValue().stream().findFirst().get().getUnsubscriptionSlot();

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UInt64.ONE));

    verifyNoMoreInteractions(attestationTopicSubscriber);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(attestationTopicSubscriber, times(spec.getNetworkingConfig().getSubnetsPerNode()))
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

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> subnetSubscriptions =
        ArgumentCaptor.forClass(Set.class);
    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(subnetSubscriptions.capture());
    assertSubnetsAreDistinct(subnetSubscriptions.getValue());
    assertUnsubscribeSlotsAreInBound(subnetSubscriptions.getValue(), ZERO);
    assertThat(subnetSubscriptions.getValue())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());
  }

  private void assertUnsubscribeSlotsAreInBound(
      Set<SubnetSubscription> subnetSubscriptions, UInt64 currentSlot) {
    UInt64 lowerBound =
        currentSlot.plus(
            UInt64.valueOf(
                (long) spec.getNetworkingConfig().getEpochsPerSubnetSubscription()
                    * spec.getGenesisSpecConfig().getSlotsPerEpoch()));
    UInt64 upperBound =
        currentSlot.plus(
            UInt64.valueOf(
                2L
                    * spec.getNetworkingConfig().getEpochsPerSubnetSubscription()
                    * spec.getGenesisSpecConfig().getSlotsPerEpoch()));
    subnetSubscriptions.forEach(
        subnetSubscription -> {
          assertThat(subnetSubscription.getUnsubscriptionSlot()).isBetween(lowerBound, upperBound);
        });
  }

  private void assertSubnetsAreDistinct(Set<SubnetSubscription> subnetSubscriptions) {
    IntSet subnetIds =
        IntOpenHashSet.toSet(
            subnetSubscriptions.stream()
                .map(SubnetSubscription::getSubnetId)
                .mapToInt(Integer::intValue));
    assertThat(subnetSubscriptions).hasSameSizeAs(subnetIds);
  }

  private StableSubnetSubscriber createStableSubnetSubscriber() {
    return new NodeBasedStableSubnetSubscriber(
        attestationTopicSubscriber, spec, Optional.of(dataStructureUtil.randomUInt256()));
  }
}
