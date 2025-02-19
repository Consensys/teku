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

package tech.pegasys.teku.networking.eth2.peers;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.Map;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class PeersStatusLogger implements SlotEventsChannel {

  private final Eth2P2PNetwork network;

  public PeersStatusLogger(final Eth2P2PNetwork network) {
    this.network = network;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (LoggingConfigurator.isLogPeersGossipScoresEnabled()) {
      final Map<StatusLogger.Peer, Double> peersScores =
          network
              .streamPeers()
              .collect(
                  Collectors.toMap(
                      peer ->
                          new StatusLogger.Peer(
                              peer.getId().toBase58(), peer.getPeerClientType().getDisplayName()),
                      Peer::getGossipScore));
      if (!peersScores.isEmpty()) {
        STATUS_LOG.peersGossipScores(peersScores);
      }
    }
  }
}
