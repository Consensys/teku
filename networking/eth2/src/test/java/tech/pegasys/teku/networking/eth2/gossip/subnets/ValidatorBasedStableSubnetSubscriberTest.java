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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.spec.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class ValidatorBasedStableSubnetSubscriberTest {
  protected Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);

  @Test
  void shouldSubscribeToNoMoreThanAttestationSubnetCount() {
    final ValidatorBasedStableSubnetSubscriber subscriber = createSubscriber(256);
    subscriber.onSlot(UInt64.ONE, 2);
    verify(network, times(ATTESTATION_SUBNET_COUNT))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < ATTESTATION_SUBNET_COUNT));
    for (int i = 0; i < ATTESTATION_SUBNET_COUNT; i++) {
      verify(network).subscribeToAttestationSubnetId(eq(i));
    }
  }

  @Test
  void shouldSubscribeToExpectedSubnetsWithNoMinimum() {
    final ValidatorBasedStableSubnetSubscriber subscriber = createSubscriber(0);
    subscriber.onSlot(UInt64.ONE, 10);
    verify(network, times(10))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < ATTESTATION_SUBNET_COUNT));
  }

  @NotNull
  private ValidatorBasedStableSubnetSubscriber createSubscriber(
      final int minimumSubnetSubscriptions) {
    return new ValidatorBasedStableSubnetSubscriber(
        new AttestationTopicSubscriber(spec, network),
        new Random(),
        spec,
        minimumSubnetSubscriptions);
  }
}
