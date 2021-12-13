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

package tech.pegasys.teku.networking.p2p.discovery;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

public class DiscoveryNetwork<P extends Peer> extends DelegatingP2PNetwork<P> {
  private static final Logger LOG = LogManager.getLogger();

  public static final String ATTESTATION_SUBNET_ENR_FIELD = "attnets";
  public static final String SYNC_COMMITTEE_SUBNET_ENR_FIELD = "syncnets";
  public static final String ETH2_ENR_FIELD = "eth2";

  private final Spec spec;
  private final P2PNetwork<P> p2pNetwork;
  private final DiscoveryService discoveryService;
  private final ConnectionManager connectionManager;
  private final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;

  private volatile Optional<EnrForkId> enrForkId = Optional.empty();

  protected DiscoveryNetwork(
      final P2PNetwork<P> p2pNetwork,
      final DiscoveryService discoveryService,
      final ConnectionManager connectionManager,
      final Spec spec,
      final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    super(p2pNetwork);
    this.p2pNetwork = p2pNetwork;
    this.discoveryService = discoveryService;
    this.connectionManager = connectionManager;
    this.spec = spec;
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    initialize();
  }

  public void initialize() {
    setPreGenesisForkInfo();
    getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5PreGenesis);

    // Set connection manager peer predicate so that we don't attempt to connect peers with
    // different fork digests
    connectionManager.addPeerPredicate(this::dontConnectPeersWithDifferentForkDigests);
  }

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.allOfFailFast(p2pNetwork.start(), discoveryService.start())
        .thenCompose(__ -> connectionManager.start())
        .thenRun(() -> getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5));
  }

  @Override
  public SafeFuture<?> stop() {
    return connectionManager
        .stop()
        .handleComposed(
            (__, err) -> {
              if (err != null) {
                LOG.warn("Error shutting down connection manager", err);
              }
              return SafeFuture.allOf(p2pNetwork.stop(), discoveryService.stop());
            });
  }

  public void addStaticPeer(final String peerAddress) {
    connectionManager.addStaticPeer(p2pNetwork.createPeerAddress(peerAddress));
  }

  @Override
  public Optional<String> getEnr() {
    return discoveryService.getEnr();
  }

  @Override
  public Optional<String> getDiscoveryAddress() {
    return discoveryService.getDiscoveryAddress();
  }

  public void setLongTermAttestationSubnetSubscriptions(Iterable<Integer> subnetIds) {
    discoveryService.updateCustomENRField(
        ATTESTATION_SUBNET_ENR_FIELD,
        currentSchemaDefinitionsSupplier
            .getAttnetsENRFieldSchema()
            .ofBits(subnetIds)
            .sszSerialize());
  }

  public void setSyncCommitteeSubnetSubscriptions(Iterable<Integer> subnetIds) {
    discoveryService.updateCustomENRField(
        SYNC_COMMITTEE_SUBNET_ENR_FIELD,
        currentSchemaDefinitionsSupplier
            .getSyncnetsENRFieldSchema()
            .ofBits(subnetIds)
            .sszSerialize());
  }

  public void setPreGenesisForkInfo() {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final Bytes4 genesisForkVersion = genesisSpec.getConfig().getGenesisForkVersion();
    final EnrForkId enrForkId =
        new EnrForkId(
            genesisSpec.miscHelpers().computeForkDigest(genesisForkVersion, Bytes32.ZERO),
            genesisForkVersion,
            SpecConfig.FAR_FUTURE_EPOCH);
    discoveryService.updateCustomENRField(ETH2_ENR_FIELD, enrForkId.sszSerialize());
    this.enrForkId = Optional.of(enrForkId);
  }

  public void setForkInfo(final ForkInfo currentForkInfo, final Optional<Fork> nextForkInfo) {
    // If no future fork is planned, set next_fork_version = current_fork_version to signal this
    final Bytes4 nextVersion =
        nextForkInfo
            .map(Fork::getCurrent_version)
            .orElse(currentForkInfo.getFork().getCurrent_version());
    // If no future fork is planned, set next_fork_epoch = FAR_FUTURE_EPOCH to signal this
    final UInt64 nextForkEpoch =
        nextForkInfo.map(Fork::getEpoch).orElse(SpecConfig.FAR_FUTURE_EPOCH);

    final Bytes4 forkDigest = currentForkInfo.getForkDigest(spec);
    final EnrForkId enrForkId = new EnrForkId(forkDigest, nextVersion, nextForkEpoch);
    final Bytes encodedEnrForkId = enrForkId.sszSerialize();

    discoveryService.updateCustomENRField(ETH2_ENR_FIELD, encodedEnrForkId);
    this.enrForkId = Optional.of(enrForkId);
  }

  private boolean dontConnectPeersWithDifferentForkDigests(DiscoveryPeer peer) {
    return enrForkId
        .map(EnrForkId::getForkDigest)
        .flatMap(
            localForkDigest ->
                peer.getEnrForkId()
                    .map(EnrForkId::getForkDigest)
                    .map(peerForkDigest -> peerForkDigest.equals(localForkDigest)))
        .orElse(false);
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

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.of(this);
  }

  public DiscoveryService getDiscoveryService() {
    return discoveryService;
  }
}
