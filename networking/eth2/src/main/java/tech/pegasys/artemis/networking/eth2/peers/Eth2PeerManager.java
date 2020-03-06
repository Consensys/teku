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

package tech.pegasys.artemis.networking.eth2.peers;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.p2p.network.PeerHandler;
import tech.pegasys.artemis.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.artemis.util.events.Subscribers;

public class Eth2PeerManager implements PeerLookup, PeerHandler {
  private static final Logger LOG = LogManager.getLogger();
  private final StatusMessageFactory statusMessageFactory;

  private final Subscribers<PeerConnectedSubscriber<Eth2Peer>> connectSubscribers =
      Subscribers.create(true);
  private final ConcurrentHashMap<NodeId, Eth2Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final BeaconChainMethods rpcMethods;
  private final PeerValidatorFactory peerValidatorFactory;

  Eth2PeerManager(
      final CombinedChainDataClient combinedChainDataClient,
      final ChainStorageClient storageClient,
      final MetricsSystem metricsSystem,
      final PeerValidatorFactory peerValidatorFactory) {
    this.statusMessageFactory = new StatusMessageFactory(storageClient);
    this.peerValidatorFactory = peerValidatorFactory;
    this.rpcMethods =
        BeaconChainMethods.create(
            DelayedExecutorAsyncRunner.create(),
            this,
            combinedChainDataClient,
            storageClient,
            metricsSystem,
            statusMessageFactory);
  }

  public static Eth2PeerManager create(
      final ChainStorageClient storageClient,
      final HistoricalChainData historicalChainData,
      final MetricsSystem metricsSystem) {
    final PeerValidatorFactory peerValidatorFactory =
        (peer, status) ->
            PeerChainValidator.create(storageClient, historicalChainData, peer, status);
    return new Eth2PeerManager(
        new CombinedChainDataClient(storageClient, historicalChainData),
        storageClient,
        metricsSystem,
        peerValidatorFactory);
  }

  @Override
  public void onConnect(final Peer peer) {
    Eth2Peer eth2Peer = new Eth2Peer(peer, rpcMethods, statusMessageFactory);
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getId(), eth2Peer) == null;
    if (!wasAdded) {
      LOG.warn("Duplicate peer connection detected. Ignoring peer.");
      return;
    }

    peer.setDisconnectRequestHandler(
        reason -> eth2Peer.sendGoodbye(convertToEth2DisconnectReason(reason)));
    if (peer.connectionInitiatedLocally()) {
      eth2Peer
          .sendStatus()
          .finish(
              () -> LOG.trace("Sent status to {}", peer.getId()),
              (err) -> LOG.debug("Failed to send status to {}: {}", peer.getId(), err));
    }
    eth2Peer.subscribeInitialStatus(
        (status) ->
            peerValidatorFactory
                .create(eth2Peer, status)
                .run()
                .finish(
                    peerIsValid -> {
                      if (peerIsValid) {
                        connectSubscribers.forEach(c -> c.onConnected(eth2Peer));
                      }
                    }));
  }

  private UnsignedLong convertToEth2DisconnectReason(final DisconnectReason reason) {
    switch (reason) {
      case TOO_MANY_PEERS:
        return GoodbyeMessage.REASON_TOO_MANY_PEERS;
      case SHUTTING_DOWN:
        return GoodbyeMessage.REASON_CLIENT_SHUT_DOWN;
      case REMOTE_FAULT:
        return GoodbyeMessage.REASON_FAULT_ERROR;
      case IRRELEVANT_NETWORK:
        return GoodbyeMessage.REASON_IRRELEVANT_NETWORK;
      case UNABLE_TO_VERIFY_NETWORK:
        return GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK;
      default:
        LOG.warn("Unknown disconnect reason: " + reason);
        return GoodbyeMessage.REASON_FAULT_ERROR;
    }
  }

  public long subscribeConnect(final PeerConnectedSubscriber<Eth2Peer> subscriber) {
    return connectSubscribers.subscribe(subscriber);
  }

  public void unsubscribeConnect(final long subscriptionId) {
    connectSubscribers.unsubscribe(subscriptionId);
  }

  @Override
  public void onDisconnect(@NotNull final Peer peer) {
    connectedPeerMap.compute(
        peer.getId(),
        (id, existingPeer) -> {
          if (peer.idMatches(existingPeer)) {
            return null;
          }
          return existingPeer;
        });
  }

  public BeaconChainMethods getBeaconChainMethods() {
    return rpcMethods;
  }

  /**
   * Look up peer by id, returning peer result regardless of validation status of the peer.
   *
   * @param nodeId The nodeId of the peer to lookup
   * @return the peer corresponding to this node id.
   */
  @Override
  public Eth2Peer getConnectedPeer(NodeId nodeId) {
    return connectedPeerMap.get(nodeId);
  }

  public Optional<Eth2Peer> getPeer(NodeId peerId) {
    return Optional.ofNullable(connectedPeerMap.get(peerId)).filter(this::peerIsReady);
  }

  public Stream<Eth2Peer> streamPeers() {
    return connectedPeerMap.values().stream().filter(this::peerIsReady);
  }

  private boolean peerIsReady(Eth2Peer peer) {
    return peer.isChainValidated();
  }

  interface PeerValidatorFactory {
    PeerChainValidator create(final Eth2Peer peer, final PeerStatus status);
  }
}
