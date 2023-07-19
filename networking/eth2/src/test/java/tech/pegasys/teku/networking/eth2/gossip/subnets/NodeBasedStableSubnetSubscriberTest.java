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
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  void shouldSubscribeToExpectedSubnets() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ZERO);
    verify(network, times(spec.getNetworkingConfig().getSubnetsPerNode()))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < attestationSubnetCount));
  }

  @Test
  void shouldSubscribeToNewSubnetsAfterExpiration() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ONE);
    subscriber.onSlot(
        UInt64.ONE.plus(
            UInt64.valueOf(
                (long) spec.getNetworkingConfig().getEpochsPerSubnetSubscription()
                    * spec.getSlotsPerEpoch(UInt64.ONE))));

    final ArgumentCaptor<Integer> subnetIdsCaptor = ArgumentCaptor.forClass(Integer.class);

    verify(network, times(spec.getNetworkingConfig().getSubnetsPerNode() * 2))
        .subscribeToAttestationSubnetId(subnetIdsCaptor.capture());
    assertThat(subnetIdsCaptor.getAllValues())
        .allMatch(subnetId -> subnetId >= 0 && subnetId < attestationSubnetCount);
    assertThat(subnetIdsCaptor.getAllValues())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode() * 2);
    assertSubnetsAreDistinct(subnetIdsCaptor.getAllValues());
  }

  @NotNull
  private NodeBasedStableSubnetSubscriber createSubscriber() {
    return new NodeBasedStableSubnetSubscriber(
        new AttestationTopicSubscriber(spec, network),
        spec,
        Optional.of(dataStructureUtil.randomUInt256()));
  }

  private void assertSubnetsAreDistinct(List<Integer> subscribedSubnetIds) {
    IntSet subnetIds =
        IntOpenHashSet.toSet(subscribedSubnetIds.stream().mapToInt(Integer::intValue));
    assertThat(subscribedSubnetIds).hasSameSizeAs(subnetIds);
  }
}
