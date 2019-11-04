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

package org.ethereum.beacon.discovery.storage;

import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.schema.EnrScheme;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.type.BytesValue;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;

// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.Bytes4;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.uint.UInt64;

public class NodeTableTest {
  private Function<UInt64, NodeRecord> homeNodeSupplier =
      (oldSeq) -> {
        try {
          return NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
              EnrScheme.V4,
              UInt64.valueOf(1),
              Bytes.EMPTY,
              new ArrayList<Pair<String, Object>>() {
                {
                  add(
                      Pair.with(
                          NodeRecord.FIELD_IP_V4,
                          BytesValue.wrap(InetAddress.getByName("127.0.0.1").getAddress())));
                  add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                  add(
                      Pair.with(
                          NodeRecord.FIELD_PKEY_SECP256K1,
                          BytesValue.fromHexString(
                              "0bfb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
                }
              });
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }
      };

  @Test
  public void testCreate() throws Exception {
    final String localhostEnr =
        "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";
    NodeRecord nodeRecord = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(localhostEnr);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            TEST_SERIALIZER,
            homeNodeSupplier,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(nodeRecord);
              return nodes;
            });
    Optional<NodeRecordInfo> extendedEnr = nodeTableStorage.get().getNode(nodeRecord.getNodeId());
    assertTrue(extendedEnr.isPresent());
    NodeRecordInfo nodeRecord2 = extendedEnr.get();
    assertEquals(
        nodeRecord.get(NodeRecord.FIELD_PKEY_SECP256K1),
        nodeRecord2.getNode().get(NodeRecord.FIELD_PKEY_SECP256K1));
    assertEquals(
        nodeTableStorage.get().getHomeNode().getNodeId(),
        homeNodeSupplier.apply(UInt64.ZERO).getNodeId());
  }

  @Test
  public void testFind() throws Exception {
    final String localhostEnr =
        "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";
    NodeRecord localHostNode = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(localhostEnr);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            TEST_SERIALIZER,
            homeNodeSupplier,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(localHostNode);
              return nodes;
            });

    // node is adjusted to be close to localhostEnr
    NodeRecord closestNode =
        NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
            EnrScheme.V4,
            UInt64.valueOf(1),
            Bytes.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(
                    Pair.with(
                        NodeRecord.FIELD_IP_V4,
                        BytesValue.wrap(InetAddress.getByName("127.0.0.2").getAddress())));
                add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                add(
                    Pair.with(
                        NodeRecord.FIELD_PKEY_SECP256K1,
                        BytesValue.fromHexString(
                            "aafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(closestNode, -1L, NodeStatus.ACTIVE, 0));
    assertEquals(
        nodeTableStorage
            .get()
            .getNode(closestNode.getNodeId())
            .get()
            .getNode()
            .get(NodeRecord.FIELD_PKEY_SECP256K1)
            .toString()
            .toUpperCase(),
        closestNode.get(NodeRecord.FIELD_PKEY_SECP256K1).toString().toUpperCase());
    NodeRecord farNode =
        NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
            EnrScheme.V4,
            UInt64.valueOf(1),
            Bytes.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(
                    Pair.with(
                        NodeRecord.FIELD_IP_V4,
                        BytesValue.wrap(InetAddress.getByName("127.0.0.3").getAddress())));
                add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                add(
                    Pair.with(
                        NodeRecord.FIELD_PKEY_SECP256K1,
                        BytesValue.fromHexString(
                            "bafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(farNode, -1L, NodeStatus.ACTIVE, 0));
    List<NodeRecordInfo> closestNodes =
        nodeTableStorage.get().findClosestNodes(closestNode.getNodeId(), 252);
    assertEquals(2, closestNodes.size());
    Set<BytesValue> publicKeys = new HashSet<>();
    closestNodes.forEach(
        n -> {
          Object key3 = n.getNode().get(NodeRecord.FIELD_PKEY_SECP256K1);
          if (key3 instanceof BytesValue) {
            publicKeys.add((BytesValue) key3);
          } else {
            publicKeys.add(BytesValue.wrap(((Bytes) key3).toArray()));
          }
        });
    //    assertTrue(publicKeys.contains(localHostNode.get(NodeRecord.FIELD_PKEY_SECP256K1)));
    //    assertTrue(publicKeys.contains(closestNode.get(NodeRecord.FIELD_PKEY_SECP256K1)));
    List<NodeRecordInfo> farNodes = nodeTableStorage.get().findClosestNodes(farNode.getNodeId(), 1);
    assertEquals(1, farNodes.size());
    assertEquals(
        farNodes.get(0).getNode().get(NodeRecord.FIELD_PKEY_SECP256K1).toString().toUpperCase(),
        farNode.get(NodeRecord.FIELD_PKEY_SECP256K1).toString().toUpperCase());
  }

  /**
   * Verifies that calculated index number is in range of [0, {@link
   * NodeTableImpl#NUMBER_OF_INDEXES})
   */
  @Test
  public void testIndexCalculation() {
    BytesValue nodeId0 =
        BytesValue.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000000");
    BytesValue nodeId1a =
        BytesValue.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000001");
    BytesValue nodeId1b =
        BytesValue.fromHexString(
            "1000000000000000000000000000000000000000000000000000000000000000");
    BytesValue nodeId1s =
        BytesValue.fromHexString(
            "1111111111111111111111111111111111111111111111111111111111111111");
    BytesValue nodeId9s =
        BytesValue.fromHexString(
            "9999999999999999999999999999999999999999999999999999999999999999");
    BytesValue nodeIdfs =
        BytesValue.fromHexString(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId0));
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId1a));
    assertEquals(16, NodeTableImpl.getNodeIndex(nodeId1b));
    assertEquals(17, NodeTableImpl.getNodeIndex(nodeId1s));
    assertEquals(153, NodeTableImpl.getNodeIndex(nodeId9s));
    assertEquals(255, NodeTableImpl.getNodeIndex(nodeIdfs));
  }
}
