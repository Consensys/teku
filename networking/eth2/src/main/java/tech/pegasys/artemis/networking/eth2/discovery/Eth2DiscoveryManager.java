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
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.etc.encode.Base58;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.javatuples.Pair;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.util.async.SafeFuture;

@SuppressWarnings("UnstableApiUsage")
public class Eth2DiscoveryManager {

  private static final Logger logger = LogManager.getLogger(Eth2DiscoveryManager.class);

  private Random rnd = new Random();

  private static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  private static final SerializerFactory TEST_SERIALIZER =
      new NodeSerializerFactory(NODE_RECORD_FACTORY);

  public static enum State {
    RUNNING,
    STOPPED
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

  DiscoveryManager dm;
  private NodeTable nodeTable;

  // core parameters for discovery service
  private String networkInterface;
  private int port;

  // configuration of discovery service
  private List<String> peers; // for setting boot nodes
  private Optional<PrivKey> privateKey = Optional.empty(); // for generating ENR records

  // the network to potentially affect with discovered peers
  private Optional<P2PNetwork<?>> network = Optional.empty();

  // event bus by which to signal other services
  private Optional<EventBus> eventBus = Optional.empty();

  public Eth2DiscoveryManager() {
    setupDiscoveryManager();
  }

  public Eth2DiscoveryManager(final P2PNetwork<?> network, final EventBus eventBus) {
    this.network = Optional.of(network);
    this.eventBus = Optional.of(eventBus);
    setupDiscoveryManager();
  }

  public NodeTable getNodeTable() {
    return nodeTable;
  }

  @Subscribe
  public void onDiscoveryRequest(final DiscoveryRequest request) {
    if (request.numPeersToFind == 0) {
      SafeFuture.of(this.stop()).reportExceptions();
      return;
    }
    if (getState().equals(State.STOPPED)) {
      SafeFuture.of(this.start()).reportExceptions();
    }
  }

  /**
   * Start discovery from stopped state
   *
   * @return Future indicating failure or State.RUNNING
   */
  public CompletableFuture<State> start() {
    if (!state.compareAndSet(State.STOPPED, State.RUNNING)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Network already started"));
    }
    eventBus.ifPresent(
        v -> {
          v.register(this);
        });
    return CompletableFuture.completedFuture(State.RUNNING);
  }

  /**
   * Stop discovery
   *
   * @return Future indicating failure or State.STOPPED
   */
  public CompletableFuture<State> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Network already stopped"));
    }
    eventBus.ifPresent(
        v -> {
          v.unregister(this);
        });
    return CompletableFuture.completedFuture(State.STOPPED);
  }

  public State getState() {
    return state.get();
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
    this.eventBus = Optional.of(eventBus);
    if (state.get().equals(State.RUNNING)) {
      this.eventBus.ifPresent(
          eb -> {
            eb.register(this);
          });
    }
  }

  public Optional<EventBus> getEventBus() {
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

  private void setupDiscoveryManager() {

    final Pair<NodeRecord, byte[]> localNodeInfo;
    try {
      localNodeInfo = createLocalNodeRecord(getPort());
    } catch (Exception e) {
      logger.error("Cannot start server on desired address/port. Stopping.");
      return;
    }
    NodeRecord localNodeRecord = localNodeInfo.getValue0();
    logger.info("localNodeRecord:" + localNodeRecord);
    // the following two lines to be removed when discovery master is next updated
    logger.info("localNodeEnr:" + localNodeRecord.asBase64());
    logger.info("localNodeId:" + localNodeRecord.getNodeId());

    byte[] localPrivKey = localNodeInfo.getValue1();

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

    nodeTable =
        new NodeTable() {
          final NodeTable nt = nodeTableStorage0.get();

          @Override
          public void save(NodeRecordInfo node) {
            nt.save(node);
            eventBus.ifPresent(
                eb -> {
                  eb.post(new DiscoveryNewPeerResponse(node));
                });

            try {
              InetAddress byAddress =
                  InetAddress.getByAddress(((Bytes) node.getNode().get(IP_V4)).toArray());

              network.ifPresent(
                  n -> {
                    String d = Base58.INSTANCE.encode(node.getNode().getNodeId().toArray());
                    String connectString =
                        "/ip4/"
                            + byAddress.getHostAddress()
                            + "/tcp/"
                            + node.getNode().get(UDP_V4)
                            + "/p2p/"
                            + d;
                    SafeFuture<?> connect = n.connect(connectString);
                    if (connect != null) {
                      connect.finish(
                          r -> {
                            logger.info("discv5 connected to:" + connectString);
                          },
                          t -> {
                            logger.error("discv5 connect failed: " + t.toString());
                          });
                    } else {
                      logger.error(
                          "connect failed with null, is this a mock? If so, the repo check expects a safe future to be handled, before being able to to test the mock");
                    }
                  });
            } catch (UnknownHostException e) {
              logger.error("Got unknown host exception for Peer Response");
            }
          }

          @Override
          public void remove(NodeRecordInfo node) {
            nt.remove(node);
          }

          @Override
          public Optional<NodeRecordInfo> getNode(Bytes nodeId) {
            return nt.getNode(nodeId);
          }

          @Override
          public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
            List<NodeRecordInfo> closestNodes = nt.findClosestNodes(nodeId, logLimit);
            eventBus.ifPresent(
                eb -> {
                  eb.post(new DiscoveryFindNodesResponse(closestNodes));
                });
            return closestNodes;
          }

          @Override
          public NodeRecord getHomeNode() {
            return nt.getHomeNode();
          }
        };

    dm =
        new DiscoveryManagerImpl(
            // delegating node table
            nodeTable,
            nodeBucketStorage0,
            localNodeRecord,
            Bytes.wrap(localPrivKey),
            NODE_RECORD_FACTORY,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Pair<NodeRecord, byte[]> createLocalNodeRecord(int port) throws UnknownHostException {
    // set local service node
    byte[] privKey1 = new byte[32];
    rnd.nextBytes(privKey1);

    Bytes localAddressBytes =
        Bytes.wrap(
            InetAddress.getByName(getNetworkInterface()).getAddress()); // 172.18.0.2 // 127.0.0.1
    Bytes localIp1 =
        Bytes.concatenate(Bytes.wrap(new byte[4 - localAddressBytes.size()]), localAddressBytes);
    NodeRecord nodeRecord1 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(IP_V4, localIp1),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Functions.derivePublicKeyFromPrivate(Bytes.wrap(privKey1))),
            Pair.with(EnrField.TCP_V4, port),
            Pair.with(UDP_V4, port));
    nodeRecord1.sign(Bytes.wrap(privKey1));
    nodeRecord1.verify();
    return new Pair(nodeRecord1, privKey1);
  }

  void setRnd(long seed) {
    this.rnd = new Random(seed);
  }

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

  public Optional<PrivKey> getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(PrivKey privateKey) {
    assert privateKey != null;
    this.privateKey = Optional.of(privateKey);
  }
}
