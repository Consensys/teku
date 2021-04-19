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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;

public abstract class PeerRequiredLocalMessageHandler<I, O> implements LocalMessageHandler<I, O> {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Optional<Eth2Peer> maybePeer,
      final I message,
      final ResponseCallback<O> callback) {
    maybePeer.ifPresentOrElse(
        peer -> onIncomingMessage(protocolId, peer, message, callback),
        () -> {
          LOG.trace(
              "Ignoring message of type {} because peer has disconnected", message.getClass());
          callback.completeWithUnexpectedError(new PeerDisconnectedException());
        });
  }

  protected abstract void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final I message,
      final ResponseCallback<O> callback);
}
