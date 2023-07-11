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

import java.util.Optional;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class NodeBasedStableSubnetSubscriberTest {
  protected Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);
  private final int attestationSubnetCount = spec.getNetworkingConfig().getAttestationSubnetCount();

  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void shouldSubscribeToNoMoreThanAttestationSubnetCount() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ONE);
    verify(network, times(attestationSubnetCount))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < attestationSubnetCount));
    for (int i = 0; i < attestationSubnetCount; i++) {
      verify(network).subscribeToAttestationSubnetId(eq(i));
    }
  }

  @Test
  void shouldSubscribeToExpectedSubnetsWithNoMinimum() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ONE);
    verify(network, times(2))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < attestationSubnetCount));
  }

  @NotNull
  private NodeBasedStableSubnetSubscriber createSubscriber() {
    return new NodeBasedStableSubnetSubscriber(
        new AttestationTopicSubscriber(spec, network),
        new Random(),
        spec,
        Optional.of(dataStructureUtil.randomUInt256()));
  }
}
