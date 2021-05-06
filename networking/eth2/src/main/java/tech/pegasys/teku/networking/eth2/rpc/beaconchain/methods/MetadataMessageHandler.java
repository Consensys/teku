/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;

public class MetadataMessageHandler
    extends PeerRequiredLocalMessageHandler<EmptyMessage, MetadataMessage> {
  private final Spec spec;
  private final MetadataMessagesFactory metadataMessagesFactory;

  public MetadataMessageHandler(final Spec spec, MetadataMessagesFactory metadataMessagesFactory) {
    this.spec = spec;
    this.metadataMessagesFactory = metadataMessagesFactory;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      Eth2Peer peer,
      EmptyMessage message,
      ResponseCallback<MetadataMessage> callback) {
    if (!peer.wantToMakeRequest()) {
      return;
    }

    final int protocolVersion = BeaconChainMethodIds.extractGetMetadataVersion(protocolId);
    final SpecMilestone milestone =
        protocolVersion == 1 ? SpecMilestone.PHASE0 : SpecMilestone.ALTAIR;
    final MetadataMessageSchema<?> schema =
        spec.forMilestone(milestone).getSchemaDefinitions().getMetadataMessageSchema();

    callback.respondAndCompleteSuccessfully(metadataMessagesFactory.createMetadataMessage(schema));
  }
}
