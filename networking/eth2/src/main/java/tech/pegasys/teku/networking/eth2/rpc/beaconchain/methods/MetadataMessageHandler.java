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

import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;

public class MetadataMessageHandler
    extends PeerRequiredLocalMessageHandler<EmptyMessage, MetadataMessage> {
  private final MetadataMessagesFactory metadataMessagesFactory;

  public MetadataMessageHandler(MetadataMessagesFactory metadataMessagesFactory) {
    this.metadataMessagesFactory = metadataMessagesFactory;
  }

  @Override
  public void onIncomingMessage(
      Eth2Peer peer, EmptyMessage message, ResponseCallback<MetadataMessage> callback) {
    if (!peer.wantToMakeRequest()) {
      return;
    }
    callback.respondAndCompleteSuccessfully(metadataMessagesFactory.createMetadataMessage());
  }
}
