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

package tech.pegasys.teku.networking.eth2.mock;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockP2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class NoOpEth2P2PNetwork extends MockP2PNetwork<Eth2Peer> implements Eth2P2PNetwork {
  private final Spec spec;

  public NoOpEth2P2PNetwork(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public void onEpoch(final UInt64 epoch) {}

  @Override
  public void subscribeToAttestationSubnetId(final int subnetId) {}

  @Override
  public void unsubscribeFromAttestationSubnetId(final int subnetId) {}

  @Override
  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIndices) {}

  @Override
  public void subscribeToSyncCommitteeSubnetId(final int subnetId) {}

  @Override
  public void unsubscribeFromSyncCommitteeSubnetId(final int subnetId) {}

  @Override
  public MetadataMessage getMetadata() {
    return spec.getGenesisSchemaDefinitions().getMetadataMessageSchema().createDefault();
  }
}
