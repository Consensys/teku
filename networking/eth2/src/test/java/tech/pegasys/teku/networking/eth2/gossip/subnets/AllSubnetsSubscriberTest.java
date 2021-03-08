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

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.util.config.Constants;

class AllSubnetsSubscriberTest {
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);

  @SuppressWarnings("unchecked")
  @Test
  void shouldSubscribeToAllSubnetsWhenCreated() {
    final StableSubnetSubscriber stableSubnetSubscriber =
        AllSubnetsSubscriber.create(attestationTopicSubscriber);
    final ArgumentCaptor<Set<SubnetSubscription>> captor = ArgumentCaptor.forClass(Set.class);
    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(captor.capture());

    final Set<SubnetSubscription> actual = captor.getValue();
    // Should subscribe to all subnets with a far future unsubscription slot
    assertThat(actual).hasSize(Constants.ATTESTATION_SUBNET_COUNT);
    assertThat(actual.stream().mapToInt(SubnetSubscription::getSubnetId))
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, Constants.ATTESTATION_SUBNET_COUNT).boxed().collect(toSet()));
    assertThat(actual.stream().map(SubnetSubscription::getUnsubscriptionSlot))
        .containsOnly(UInt64.MAX_VALUE);

    stableSubnetSubscriber.onSlot(UInt64.ONE, 1);
    verifyNoMoreInteractions(attestationTopicSubscriber);
  }
}
