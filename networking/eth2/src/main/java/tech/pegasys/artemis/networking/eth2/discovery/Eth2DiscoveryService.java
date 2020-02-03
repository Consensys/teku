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
import static org.ethereum.beacon.discovery.schema.NodeRecord.createNodeRecord;
import static org.ethereum.beacon.discovery.schema.NodeRecord.createNodeRecord;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import io.libp2p.etc.encode.Base58;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
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
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.task.DiscoveryTaskManager;
import org.ethereum.beacon.discovery.util.Functions;
import org.javatuples.Pair;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryNetwork;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryPeer;
import tech.pegasys.artemis.networking.eth2.discovery.nodetable.EventEmittingNodeTable;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.util.async.SafeFuture;

@SuppressWarnings("UnstableApiUsage")
public class Eth2DiscoveryService extends Service implements DiscoveryNetwork {

  private final Logger logger = LogManager.getLogger();

  private final DiscoveryManager dm;
  private final NodeTable nodeTable;

  // event bus by which to signal other services
  private final EventBus eventBus;
  private final NetworkConfig config;
//  private final DiscoveryTaskManager dtm;

  public Eth2DiscoveryService(final NetworkConfig networkConfig, final EventBus eventBus) {
    this.config = networkConfig;
    this.eventBus = eventBus;

    final NodeRecord localNodeRecord;
    try {
      localNodeRecord =
          createNodeRecord(
              config.getDiscoveryPrivateKey(),
              config.getNetworkInterface(),
              config.getListenPort());
      logger.info("localNodeRecord:" + localNodeRecord);
      // the following two lines to be removed when discovery master is next updated
      logger.info("localNodeEnr:" + localNodeRecord.asBase64());
      logger.info("localNodeId:" + localNodeRecord.getNodeId());

      Database database0 = Database.inMemoryDB();
      NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
      NodeRecordFactory NODE_RECORD_FACTORY =
          new NodeRecordFactory(new IdentitySchemaV4Interpreter());
      SerializerFactory TEST_SERIALIZER = new NodeSerializerFactory(NODE_RECORD_FACTORY);
      NodeTableStorage nodeTableStorage0 =
          nodeTableStorageFactory.createTable(
              database0,
              TEST_SERIALIZER,
              (oldSeq) -> localNodeRecord,
              () -> {
                if (config.getDiscoveryPeers().size() > 0) {
                  return config.getDiscoveryPeers().stream()
                      .map(this::setupRemoteNode)
                      .collect(Collectors.toList());
                } else {
                  return Collections.emptyList();
                }
              }); // Collections.singletonList(remoteNodeRecord)
      NodeBucketStorage nodeBucketStorage =
          nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, localNodeRecord);

      this.nodeTable = new EventEmittingNodeTable(nodeTableStorage0.get(), eventBus);

      this.dm =
          new DiscoveryManagerImpl(
              // delegating node table
              nodeTable,
              nodeBucketStorage,
              localNodeRecord,
              Bytes.wrap(config.getDiscoveryPrivateKey()),
              NODE_RECORD_FACTORY,
              Schedulers.createDefault().newSingleThreadDaemon("server-1"),
              Schedulers.createDefault().newSingleThreadDaemon("client-1"));

      Collection<Consumer<NodeRecord>> consumers = new ArrayList<>();
      consumers.add(
          nr -> {
            logger.debug(() -> String.format("On %s, discovery search found: %s", nodeTable.getHomeNode(), nr));
            nodeTable.save(NodeRecordInfo.createDefault(nr));
          });
//      dtm =
//          new DiscoveryTaskManager(
//              dm,
//              nodeTable,
//              nodeBucketStorage,
//              localNodeRecord,
//              Schedulers.createDefault().newSingleThreadDaemon("discovery-search"),
//              false,
//              false,
//              consumers);
    } catch (Exception e) {
      logger.error("Error constructing local node record: " + e.getMessage());
      throw new RuntimeException("Error constructing DiscoveryService");
    }
  }

  @Override
  public SafeFuture<State> doStart() {
    dm.start();
//    dtm.start();
    return SafeFuture.completedFuture(this.getState());
  }

  @Override
  public SafeFuture<State> doStop() {
    dm.stop();
    return SafeFuture.completedFuture(this.getState());
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  @Override
  public void findPeers() {
//    dtm.start();
    streamPeers()
        .forEach(
            dp -> {
              logger.info(
                  () ->
                      String.format(
                          "On %s, findpeers, finding noderecord: %s",
                          nodeTable.getHomeNode(), dp.getNodeRecord()));
              SafeFuture.of(dm.findNodes(dp.getNodeRecord(), 256)).reportExceptions();
            });

  }

  @Override
  public Stream<DiscoveryPeer> streamPeers() {
    // use logLimit of 0 to retrieve all entries in the node table
    return nodeTable.findClosestNodes(nodeTable.getHomeNode().getNodeId(), 256).stream()
        .map(n -> DiscoveryPeer.fromNodeRecord(n.getNode()));
  }

  private NodeRecord setupRemoteNode(final String remoteHostEnr) {
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    remoteNodeRecord.verify();
    logger.info("remoteNodeRecord:" + remoteNodeRecord);

    // the following two lines to be removed when discovery master is next updated
    logger.info("remoteEnr:" + remoteNodeRecord.asBase64());
    logger.info("remoteNodeId:" + remoteNodeRecord.getNodeId());
    logger.info(
        "remoteNodeIdBase58:" + Base58.INSTANCE.encode(remoteNodeRecord.getNodeId().toArray()));

    return remoteNodeRecord;
  }

  @VisibleForTesting
  public NodeTable getNodeTable() {
    return nodeTable;
  }
}
