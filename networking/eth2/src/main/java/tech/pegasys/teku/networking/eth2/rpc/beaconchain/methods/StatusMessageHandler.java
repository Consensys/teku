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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;

public class StatusMessageHandler implements LocalMessageHandler<StatusMessage, StatusMessage> {
  private static final Logger LOG = LogManager.getLogger();
  private final StatusMessageFactory statusMessageFactory;

  public StatusMessageHandler(final StatusMessageFactory statusMessageFactory) {
    this.statusMessageFactory = statusMessageFactory;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final StatusMessage message,
      final ResponseCallback<StatusMessage> callback) {
    LOG.trace("Peer {} sent status.", peer.getId());
    final PeerStatus status = PeerStatus.fromStatusMessage(message);
    peer.updateStatus(status);
    callback.respond(statusMessageFactory.createStatusMessage());
    callback.completeSuccessfully();
  }
}
