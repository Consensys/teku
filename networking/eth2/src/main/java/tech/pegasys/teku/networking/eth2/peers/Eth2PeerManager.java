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

package tech.pegasys.teku.networking.eth2.peers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.networking.eth2.AttestationSubnetService;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.Cancellable;
import tech.pegasys.teku.util.async.RootCauseExceptionHandler;
import tech.pegasys.teku.util.events.Subscribers;

public class Eth2PeerManager implements PeerLookup, PeerHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;

  private final Subscribers<PeerConnectedSubscriber<Eth2Peer>> connectSubscribers =
      Subscribers.create(true);
  private final ConcurrentHashMap<NodeId, Eth2Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final BeaconChainMethods rpcMethods;
  private final PeerValidatorFactory peerValidatorFactory;

  private final Duration eth2RpcPingInterval;
  private final int eth2RpcOutstandingPingThreshold;

  private final Duration eth2StatusUpdateInterval;

  Eth2PeerManager(
      final AsyncRunner asyncRunner,
      final CombinedChainDataClient combinedChainDataClient,
      final RecentChainData storageClient,
      final MetricsSystem metricsSystem,
      final PeerValidatorFactory peerValidatorFactory,
      final AttestationSubnetService attestationSubnetService,
      final RpcEncoding rpcEncoding,
      final Duration eth2RpcPingInterval,
      final int eth2RpcOutstandingPingThreshold,
      Duration eth2StatusUpdateInterval) {
    this.asyncRunner = asyncRunner;
    this.statusMessageFactory = new StatusMessageFactory(storageClient);
    metadataMessagesFactory = new MetadataMessagesFactory();
    attestationSubnetService.subscribeToUpdates(metadataMessagesFactory);
    this.peerValidatorFactory = peerValidatorFactory;
    this.rpcMethods =
        BeaconChainMethods.create(
            asyncRunner,
            this,
            combinedChainDataClient,
            storageClient,
            metricsSystem,
            statusMessageFactory,
            metadataMessagesFactory,
            rpcEncoding);
    this.eth2RpcPingInterval = eth2RpcPingInterval;
    this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
    this.eth2StatusUpdateInterval = eth2StatusUpdateInterval;
  }

  public static Eth2PeerManager create(
      final AsyncRunner asyncRunner,
      final RecentChainData storageClient,
      final StorageQueryChannel historicalChainData,
      final MetricsSystem metricsSystem,
      final AttestationSubnetService attestationSubnetService,
      final RpcEncoding rpcEncoding,
      final Duration eth2RpcPingInterval,
      final int eth2RpcOutstandingPingThreshold,
      Duration eth2StatusUpdateInterval) {
    final PeerValidatorFactory peerValidatorFactory =
        (peer, status) ->
            PeerChainValidator.create(storageClient, historicalChainData, peer, status);
    return new Eth2PeerManager(
        asyncRunner,
        new CombinedChainDataClient(storageClient, historicalChainData),
        storageClient,
        metricsSystem,
        peerValidatorFactory,
        attestationSubnetService,
        rpcEncoding,
        eth2RpcPingInterval,
        eth2RpcOutstandingPingThreshold,
        eth2StatusUpdateInterval);
  }

  public MetadataMessage getMetadataMessage() {
    return metadataMessagesFactory.createMetadataMessage();
  }

  private void setUpPeriodicTasksForPeer(Eth2Peer peer) {
    Cancellable periodicStatusUpdateTask = periodicallyUpdatePeerStatus(peer);
    Cancellable periodicPingTask = periodicallyPingPeer(peer);
    peer.subscribeDisconnect(
        () -> {
          periodicStatusUpdateTask.cancel();
          periodicPingTask.cancel();
        });
  }

  Cancellable periodicallyUpdatePeerStatus(Eth2Peer peer) {
    return asyncRunner.runWithFixedDelay(
        () ->
            peer.sendStatus()
                .finish(
                    () -> LOG.trace("Updated status for peer {}", peer),
                    err -> LOG.debug("Exception updating status for peer {}", peer, err)),
        eth2StatusUpdateInterval.getSeconds(),
        TimeUnit.SECONDS,
        err -> LOG.debug("Exception calling runnable for updating peer status.", err));
  }

  Cancellable periodicallyPingPeer(Eth2Peer peer) {
    return asyncRunner.runWithFixedDelay(
        () -> sendPeriodicPing(peer),
        eth2RpcPingInterval.toMillis(),
        TimeUnit.MILLISECONDS,
        err -> LOG.debug("Exception calling runnable for pinging peer", err));
  }

  @Override
  public void onConnect(final Peer peer) {
    Eth2Peer eth2Peer =
        new Eth2Peer(peer, rpcMethods, statusMessageFactory, metadataMessagesFactory);
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
              RootCauseExceptionHandler.builder()
                  .addCatch(
                      RpcException.class,
                      err -> LOG.trace("Status message rejected by {}: {}", peer.getId(), err))
                  .defaultCatch(
                      err -> LOG.debug("Failed to send status to {}: {}", peer.getId(), err)));
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
                        setUpPeriodicTasksForPeer(eth2Peer);
                      }
                    },
                    error -> LOG.debug("Error while validating peer", error)));
  }

  @VisibleForTesting
  void sendPeriodicPing(Eth2Peer peer) {
    if (peer.getOutstandingPings() >= eth2RpcOutstandingPingThreshold) {
      LOG.debug("Disconnecting the peer {} due to PING timeout.", peer.getId());
      peer.disconnectCleanly(DisconnectReason.REMOTE_FAULT);
    } else {
      peer.sendPing()
          .finish(
              i -> LOG.trace("Periodic ping returned {} from {}", i, peer.getId()),
              t -> LOG.debug("Ping request failed for peer {}", peer.getId(), t));
    }
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
  public Optional<Eth2Peer> getConnectedPeer(NodeId nodeId) {
    return Optional.ofNullable(connectedPeerMap.get(nodeId));
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
