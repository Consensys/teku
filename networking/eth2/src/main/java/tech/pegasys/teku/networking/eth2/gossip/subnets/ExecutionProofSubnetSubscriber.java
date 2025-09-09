/*
 * Copyright Consensys Software Inc., 2025
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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.stream.IntStream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;
import tech.pegasys.teku.spec.Spec;

public class ExecutionProofSubnetSubscriber implements SlotEventsChannel {
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final ZkChainConfiguration zkChainConfiguration;
  private final UInt256 nodeId;
  private final Spec spec;

  private IntSet currentSubscribedSubnets = IntSet.of();
  private UInt64 lastEpoch = UInt64.MAX_VALUE;

  public ExecutionProofSubnetSubscriber(
      final Spec spec,
      final Eth2P2PNetwork eth2P2PNetwork,
      final UInt256 nodeId,
      final ZkChainConfiguration zkChainConfiguration) {
    this.spec = spec;
    this.eth2P2PNetwork = eth2P2PNetwork;
    this.nodeId = nodeId;
    this.zkChainConfiguration = zkChainConfiguration;
  }

  private void subscribeToSubnets(final Collection<Integer> newSubscriptions) {

    IntOpenHashSet newSubscriptionsSet = new IntOpenHashSet(newSubscriptions);

    for (int oldSubnet : currentSubscribedSubnets) {
      if (!newSubscriptionsSet.contains(oldSubnet)) {
        eth2P2PNetwork.unsubscribeFromExecutionProofSubnetId(oldSubnet);
      }
    }

    for (int newSubnet : newSubscriptionsSet) {
      if (!currentSubscribedSubnets.contains(newSubnet)) {
        eth2P2PNetwork.subscribeToExecutionProofSubnetId(newSubnet);
      }
    }

    currentSubscribedSubnets = newSubscriptionsSet;
  }

  private void onEpoch(final UInt64 epoch) {
    subscribeToSubnets(
        IntStream.range(0, MAX_EXECUTION_PROOF_SUBNETS.intValue()).boxed().collect(toList()));
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    if (!epoch.equals(lastEpoch)) {
      lastEpoch = epoch;
      onEpoch(epoch);
    }
  }
}
