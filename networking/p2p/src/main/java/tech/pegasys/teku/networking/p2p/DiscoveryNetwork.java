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

package tech.pegasys.teku.networking.p2p;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_FORK_VERSION;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.logging.StatusLogger;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.discovery.noop.NoOpDiscoveryService;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;

public class DiscoveryNetwork<P extends Peer> extends DelegatingP2PNetwork<P> {
  private static final String ATTESTATION_SUBNET_ENR_FIELD = "attnets";
  private static final String ETH2_ENR_FIELD = "eth2";
  private static final Logger LOG = LogManager.getLogger();

  private final P2PNetwork<P> p2pNetwork;
  private final DiscoveryService discoveryService;
  private final ConnectionManager connectionManager;

  DiscoveryNetwork(
      final P2PNetwork<P> p2pNetwork,
      final DiscoveryService discoveryService,
      final ConnectionManager connectionManager) {
    super(p2pNetwork);
    this.p2pNetwork = p2pNetwork;
    this.discoveryService = discoveryService;
    this.connectionManager = connectionManager;
    initialize();
  }

  public void initialize() {
    setBootNodeForkInfo();
    getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5);
  }

  public static <P extends Peer> DiscoveryNetwork<P> create(
      final P2PNetwork<P> p2pNetwork,
      final ReputationManager reputationManager,
      final NetworkConfig p2pConfig) {
    final DiscoveryService discoveryService = createDiscoveryService(p2pConfig);
    final ConnectionManager connectionManager =
        new ConnectionManager(
            discoveryService,
            reputationManager,
            DelayedExecutorAsyncRunner.create(),
            p2pNetwork,
            p2pConfig.getStaticPeers().stream()
                .map(p2pNetwork::createPeerAddress)
                .collect(toList()),
            p2pConfig.getTargetPeerRange());
    return new DiscoveryNetwork<>(p2pNetwork, discoveryService, connectionManager);
  }

  private static DiscoveryService createDiscoveryService(final NetworkConfig p2pConfig) {
    final DiscoveryService discoveryService;
    if (p2pConfig.isDiscoveryEnabled()) {
      discoveryService =
          DiscV5Service.create(
              Bytes.wrap(p2pConfig.getPrivateKey().raw()),
              p2pConfig.getNetworkInterface(),
              p2pConfig.getListenPort(),
              p2pConfig.getAdvertisedIp(),
              p2pConfig.getAdvertisedPort(),
              p2pConfig.getBootnodes());
    } else {
      discoveryService = new NoOpDiscoveryService();
    }
    return discoveryService;
  }

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.allOfFailFast(p2pNetwork.start(), discoveryService.start())
        .thenCompose(__ -> connectionManager.start())
        .thenRun(() -> getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5));
  }

  @Override
  public void stop() {
    connectionManager
        .stop()
        .exceptionally(
            error -> {
              LOG.error("Failed to stop connection manager", error);
              return null;
            })
        .always(
            () -> {
              p2pNetwork.stop();
              discoveryService.stop().reportExceptions();
            });
  }

  public void addStaticPeer(final String peerAddress) {
    connectionManager.addStaticPeer(p2pNetwork.createPeerAddress(peerAddress));
  }

  @Override
  public Optional<String> getEnr() {
    return discoveryService.getEnr();
  }

  public void setLongTermAttestationSubnetSubscriptions(Iterable<Integer> subnetIds) {
    discoveryService.updateCustomENRField(
        ATTESTATION_SUBNET_ENR_FIELD,
        new Bitvector(subnetIds, ATTESTATION_SUBNET_COUNT).serialize());
  }

  public void setBootNodeForkInfo() {
    final EnrForkId enrForkId =
        new EnrForkId(
            compute_fork_digest(GENESIS_FORK_VERSION, Bytes32.ZERO),
            GENESIS_FORK_VERSION,
            FAR_FUTURE_EPOCH);
    discoveryService.updateCustomENRField(
        ETH2_ENR_FIELD, SimpleOffsetSerializer.serialize(enrForkId));
  }

  public void setForkInfo(final ForkInfo currentForkInfo, final Optional<Fork> nextForkInfo) {
    // If no future fork is planned, set next_fork_version = current_fork_version to signal this
    final Bytes4 nextVersion =
        nextForkInfo
            .map(Fork::getCurrent_version)
            .orElse(currentForkInfo.getFork().getCurrent_version());
    // If no future fork is planned, set next_fork_epoch = FAR_FUTURE_EPOCH to signal this
    final UnsignedLong nextForkEpoch = nextForkInfo.map(Fork::getEpoch).orElse(FAR_FUTURE_EPOCH);

    final EnrForkId enrForkId =
        new EnrForkId(currentForkInfo.getForkDigest(), nextVersion, nextForkEpoch);
    discoveryService.updateCustomENRField(
        ETH2_ENR_FIELD, SimpleOffsetSerializer.serialize(enrForkId));
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<P> subscriber) {
    return p2pNetwork.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    p2pNetwork.unsubscribeConnect(subscriptionId);
  }

  @Override
  public Optional<P> getPeer(final NodeId id) {
    return p2pNetwork.getPeer(id);
  }

  @Override
  public Stream<P> streamPeers() {
    return p2pNetwork.streamPeers();
  }
}
