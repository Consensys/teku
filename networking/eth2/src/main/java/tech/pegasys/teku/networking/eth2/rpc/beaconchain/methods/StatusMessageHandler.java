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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;

public class StatusMessageHandler
    extends PeerRequiredLocalMessageHandler<StatusMessage, StatusMessage> {
  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("StaticAssignmentOfThrowable")
  static final RpcException NODE_NOT_READY =
      new RpcException(
          RpcResponseStatus.SERVER_ERROR_CODE, "Node is initializing, status unavailable.");

  private final StatusMessageFactory statusMessageFactory;

  public StatusMessageHandler(final StatusMessageFactory statusMessageFactory) {
    this.statusMessageFactory = statusMessageFactory;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final StatusMessage message,
      final ResponseCallback<StatusMessage> callback) {
    LOG.trace("Peer {} sent status {}", peer.getId(), message);
    if (!peer.allowedToMakeRequest()) {
      return;
    }
    final PeerStatus status = PeerStatus.fromStatusMessage(message);
    peer.updateStatus(status);

    final Optional<StatusMessage> localStatus = statusMessageFactory.createStatusMessage();
    if (localStatus.isPresent()) {
      callback.respondAndCompleteSuccessfully(localStatus.get());
    } else {
      LOG.warn(
          "Node is not ready to receive p2p traffic. Responding to incoming status message with an error.");
      callback.completeWithErrorResponse(NODE_NOT_READY);
    }
  }
}
