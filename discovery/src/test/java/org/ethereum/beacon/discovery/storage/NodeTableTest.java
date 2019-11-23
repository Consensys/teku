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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.junit.jupiter.api.Test;

public class NodeTableTest {
  final String LOCALHOST_BASE64 =
      "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQMRo9bfkceoY0W04hSgYU5Q1R_mmq3Qp9pBPMAIduKrAYN1ZHCCdl8=";
  private Function<UInt64, NodeRecord> HOME_NODE_SUPPLIER =
      (oldSeq) -> TestUtil.generateUnverifiedNode(30303).getValue1();

  @Test
  public void testCreate() throws Exception {
    NodeRecord nodeRecord = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(LOCALHOST_BASE64);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            TEST_SERIALIZER,
            HOME_NODE_SUPPLIER,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(nodeRecord);
              return nodes;
            });
    Optional<NodeRecordInfo> extendedEnr = nodeTableStorage.get().getNode(nodeRecord.getNodeId());
    assertTrue(extendedEnr.isPresent());
    NodeRecordInfo nodeRecord2 = extendedEnr.get();
    assertEquals(
        nodeRecord.get(EnrFieldV4.PKEY_SECP256K1),
        nodeRecord2.getNode().get(EnrFieldV4.PKEY_SECP256K1));
    assertEquals(
        nodeTableStorage.get().getHomeNode().getNodeId(),
        HOME_NODE_SUPPLIER.apply(UInt64.ZERO).getNodeId());
  }

  @Test
  public void testFind() throws Exception {
    NodeRecord localHostNode = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(LOCALHOST_BASE64);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            TEST_SERIALIZER,
            HOME_NODE_SUPPLIER,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(localHostNode);
              return nodes;
            });

    // node is adjusted to be close to localhostEnr
    NodeRecord closestNode = TestUtil.generateUnverifiedNode(30267).getValue1();
    nodeTableStorage.get().save(new NodeRecordInfo(closestNode, -1L, NodeStatus.ACTIVE, 0));
    assertEquals(
        nodeTableStorage
            .get()
            .getNode(closestNode.getNodeId())
            .get()
            .getNode()
            .get(EnrFieldV4.PKEY_SECP256K1),
        closestNode.get(EnrFieldV4.PKEY_SECP256K1));
    // node is adjusted to be far from localhostEnr
    NodeRecord farNode = TestUtil.generateUnverifiedNode(30304).getValue1();
    nodeTableStorage.get().save(new NodeRecordInfo(farNode, -1L, NodeStatus.ACTIVE, 0));
    List<NodeRecordInfo> closestNodes =
        nodeTableStorage.get().findClosestNodes(closestNode.getNodeId(), 254);
    assertEquals(2, closestNodes.size());
    Set<Bytes> publicKeys = new HashSet<>();
    closestNodes.forEach(n -> publicKeys.add((Bytes) n.getNode().get(EnrFieldV4.PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(localHostNode.get(EnrFieldV4.PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(closestNode.get(EnrFieldV4.PKEY_SECP256K1)));
    List<NodeRecordInfo> farNodes = nodeTableStorage.get().findClosestNodes(farNode.getNodeId(), 1);
    assertEquals(1, farNodes.size());
    assertEquals(
        farNodes.get(0).getNode().get(EnrFieldV4.PKEY_SECP256K1),
        farNode.get(EnrFieldV4.PKEY_SECP256K1));
  }

  /**
   * Verifies that calculated index number is in range of [0, {@link
   * NodeTableImpl#NUMBER_OF_INDEXES})
   */
  @Test
  public void testIndexCalculation() {
    Bytes nodeId0 =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1a =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
    Bytes nodeId1b =
        Bytes.fromHexString("1000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1s =
        Bytes.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");
    Bytes nodeId9s =
        Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999");
    Bytes nodeIdfs =
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId0));
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId1a));
    assertEquals(16, NodeTableImpl.getNodeIndex(nodeId1b));
    assertEquals(17, NodeTableImpl.getNodeIndex(nodeId1s));
    assertEquals(153, NodeTableImpl.getNodeIndex(nodeId9s));
    assertEquals(255, NodeTableImpl.getNodeIndex(nodeIdfs));
  }
}
