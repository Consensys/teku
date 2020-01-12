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

package tech.pegasys.artemis.networking.eth2.discovery;

import static org.ethereum.beacon.discovery.schema.EnrField.IP_V4;
import static org.ethereum.beacon.discovery.schema.EnrField.UDP_V4;

import com.google.common.eventbus.EventBus;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.DiscoveryManagerImpl;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.format.SerializerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.javatuples.Pair;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.service.serviceutils.Service.State;
import tech.pegasys.artemis.service.serviceutils.TypedService;
import tech.pegasys.artemis.util.async.SafeFuture;

@SuppressWarnings("UnstableApiUsage")
public class Eth2DiscoveryService extends TypedService<State> implements DiscoveryNetwork {

  private static final Logger logger = LogManager.getLogger(Eth2DiscoveryService.class);

  //  private static Random rnd = new Random();

  private static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  private static final SerializerFactory TEST_SERIALIZER =
      new NodeSerializerFactory(NODE_RECORD_FACTORY);

  DiscoveryManager dm;
  private NodeTable nodeTable;

  // core parameters for discovery service
  private String networkInterface;
  private int port;

  // configuration of discovery service
  private List<String> peers; // for setting boot nodes
  private byte[] privateKey; // for generating ENR records

  // the network to potentially affect with discovered peers
  private Optional<P2PNetwork<?>> network = Optional.empty();

  // event bus by which to signal other services
  private EventBus eventBus;

  public NodeTable getNodeTable() {
    return nodeTable;
  }

  /**
   * Start discovery from stopped state
   *
   * @return Future indicating failure or State.RUNNING
   */
  @Override
  public SafeFuture<State> doStart() {
    eventBus.register(this);
    return SafeFuture.completedFuture(this.getState());
  }

  /**
   * Stop discovery
   *
   * @return Future indicating failure or State.STOPPED
   */
  @Override
  public SafeFuture<State> doStop() {
    eventBus.unregister(this);
    return SafeFuture.completedFuture(this.getState());
  }

  public void setNetwork(P2PNetwork<?> network) {
    assert network != null;
    this.network = Optional.of(network);
  }

  public Optional<P2PNetwork<?>> getNetwork() {
    return network;
  }

  public void setEventBus(EventBus eventBus) {
    assert eventBus != null;
    this.eventBus = eventBus;
    if (this.getState().equals(State.RUNNING)) {
      this.eventBus.register(this);
    }
  }

  @Override
  public void findPeers() {
    // nop - is this to start?
  }

  @Override
  public void subscribePeerDiscovery(DiscoveryPeerSubscriber subscriber) {
    eventBus.register(subscriber);
  }

  @Override
  public void unsubscribePeerDiscovery(DiscoveryPeerSubscriber subscriber) {
    eventBus.unregister(subscriber);
  }

  @Override
  public Stream<DiscoveryPeer> streamPeers() {
    // use logLimit of 0 to retrieve all entries in the node table
    return getNodeTable().findClosestNodes(getNodeTable().getHomeNode().getNodeId(), 0).stream()
        .map(
            n -> {
              return DiscoveryPeer.getDiscoveryPeerFromNodeRecord(n.getNode());
            });
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  private NodeRecord setupRemoteNode(final String remoteHostEnr) {
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    remoteNodeRecord.verify();
    logger.info("remoteNodeRecord:" + remoteNodeRecord);

    // the following two lines to be removed when discovery master is next updated
    logger.info("remoteEnr:" + remoteNodeRecord.asBase64());
    logger.info("remoteNodeId:" + remoteNodeRecord.getNodeId());

    return remoteNodeRecord;
  }

  Eth2DiscoveryService build() {
    final NodeRecord localNodeRecord;
    try {
      localNodeRecord = createLocalNodeRecord(getPrivateKey(), getNetworkInterface(), getPort());
    } catch (Exception e) {
      logger.error("Error constructing local node record: " + e.getMessage());
      return this;
    }

    logger.info("localNodeRecord:" + localNodeRecord);
    // the following two lines to be removed when discovery master is next updated
    logger.info("localNodeEnr:" + localNodeRecord.asBase64());
    logger.info("localNodeId:" + localNodeRecord.getNodeId());

    Database database0 = Database.inMemoryDB();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeTableStorage nodeTableStorage0 =
        nodeTableStorageFactory.createTable(
            database0,
            TEST_SERIALIZER,
            (oldSeq) -> localNodeRecord,
            () -> {
              if (peers != null && peers.size() > 0) {
                return peers.stream().map(this::setupRemoteNode).collect(Collectors.toList());
              } else {
                return Collections.emptyList();
              }
            }); // Collections.singletonList(remoteNodeRecord)
    NodeBucketStorage nodeBucketStorage0 =
        nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, localNodeRecord);

    nodeTable = new BusNodeTable(nodeTableStorage0.get(), eventBus);

    dm =
        new DiscoveryManagerImpl(
            // delegating node table
            nodeTable,
            nodeBucketStorage0,
            localNodeRecord,
            Bytes.wrap(getPrivateKey()),
            NODE_RECORD_FACTORY,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));
    return this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static NodeRecord createLocalNodeRecord(
      byte[] privateKey, String networkInterface, int port) throws UnknownHostException {
    Bytes localAddressBytes =
        Bytes.wrap(InetAddress.getByName(networkInterface).getAddress()); // 172.18.0.2 // 127.0.0.1
    Bytes localIp1 =
        Bytes.concatenate(Bytes.wrap(new byte[4 - localAddressBytes.size()]), localAddressBytes);
    NodeRecord nodeRecord1 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(IP_V4, localIp1),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Functions.derivePublicKeyFromPrivate(Bytes.wrap(privateKey))),
            Pair.with(EnrField.TCP_V4, port),
            Pair.with(UDP_V4, port));
    nodeRecord1.sign(Bytes.wrap(privateKey));
    nodeRecord1.verify();
    return nodeRecord1;
  }

  //  void setRnd(long seed) {
  //    rnd = new Random(seed);
  //  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public List<String> getPeers() {
    return peers;
  }

  public void setPeers(List<String> peers) {
    this.peers = peers;
  }

  public String getNetworkInterface() {
    return networkInterface;
  }

  public void setNetworkInterface(String networkInterface) {
    this.networkInterface = networkInterface;
  }

  public byte[] getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(byte[] privateKey) {
    assert privateKey != null;
    this.privateKey = privateKey;
  }
}
