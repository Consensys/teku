/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beacon.sync.fetch;

import com.google.common.base.Suppliers;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class MilestoneBasedFetchBlockTaskFactory implements FetchBlockTaskFactory {

  private final Spec spec;

  private final Map<SpecMilestone, FetchBlockTaskFactory> registeredFetchBlockTaskFactories =
      new HashMap<>();

  public MilestoneBasedFetchBlockTaskFactory(
      final Spec spec, final P2PNetwork<Eth2Peer> eth2Network) {
    this.spec = spec;

    final FetchBlockTaskFactory blockTaskFactory =
        (__, blockRoot) -> new FetchBlockTask(eth2Network, blockRoot);

    // Not needed for all milestones
    final Supplier<FetchBlockTaskFactory> blockAndBlobsSidecarTaskFactory =
        Suppliers.memoize(
            () -> (__, blockRoot) -> new FetchBlockAndBlobsSidecarTask(eth2Network, blockRoot));

    spec.getEnabledMilestones()
        .forEach(
            forkAndSpecMilestone -> {
              final SpecMilestone milestone = forkAndSpecMilestone.getSpecMilestone();
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.EIP4844)) {
                registeredFetchBlockTaskFactories.put(
                    milestone, blockAndBlobsSidecarTaskFactory.get());
              } else {
                registeredFetchBlockTaskFactories.put(milestone, blockTaskFactory);
              }
            });
  }

  @Override
  public FetchBlockTask create(final UInt64 currentSlot, final Bytes32 blockRoot) {
    final SpecMilestone currentMilestone = spec.atSlot(currentSlot).getMilestone();
    return registeredFetchBlockTaskFactories.get(currentMilestone).create(currentSlot, blockRoot);
  }
}
