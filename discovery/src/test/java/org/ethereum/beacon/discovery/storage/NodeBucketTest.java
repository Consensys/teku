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

import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.stream.IntStream;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

public class NodeBucketTest {
  private final Random rnd = new Random();

  private NodeRecordInfo generateUniqueRecord(int portInc) {
    NodeRecord nodeRecord = TestUtil.generateUnverifiedNode(30303 + portInc).getValue1();
    return new NodeRecordInfo(nodeRecord, 0L, NodeStatus.ACTIVE, 0);
  }

  @Test
  public void testBucket() {
    NodeBucket nodeBucket = new NodeBucket();
    IntStream.range(0, 20).forEach(value -> nodeBucket.put(generateUniqueRecord(value)));
    assertEquals(NodeBucket.K, nodeBucket.size());
    assertEquals(NodeBucket.K, nodeBucket.getNodeRecords().size());

    long lastRetrySaved = -1L;
    for (NodeRecordInfo nodeRecordInfo : nodeBucket.getNodeRecords()) {
      assert nodeRecordInfo.getLastRetry()
          >= lastRetrySaved; // Assert sorted by last retry, latest retry in the end
      lastRetrySaved = nodeRecordInfo.getLastRetry();
    }
    NodeRecordInfo willNotInsertNode =
        new NodeRecordInfo(generateUniqueRecord(25).getNode(), -2L, NodeStatus.ACTIVE, 0);
    nodeBucket.put(willNotInsertNode);
    assertFalse(nodeBucket.contains(willNotInsertNode));
    NodeRecordInfo willInsertNode =
        new NodeRecordInfo(generateUniqueRecord(26).getNode(), 1001L, NodeStatus.ACTIVE, 0);
    NodeRecordInfo top =
        nodeBucket.getNodeRecords().get(NodeBucket.K - 1); // latest retry should be kept
    NodeRecordInfo bottom = nodeBucket.getNodeRecords().get(0);
    nodeBucket.put(willInsertNode);
    assertTrue(nodeBucket.contains(willInsertNode));
    assertTrue(nodeBucket.contains(top));
    assertFalse(nodeBucket.contains(bottom));
    NodeRecordInfo willInsertNode2 =
        new NodeRecordInfo(willInsertNode.getNode(), 1002L, NodeStatus.ACTIVE, 0);
    nodeBucket.put(willInsertNode2); // replaces willInsertNode with better last retry
    assertTrue(nodeBucket.getNodeRecords().contains(willInsertNode2));
    NodeRecordInfo willNotInsertNode3 =
        new NodeRecordInfo(willInsertNode.getNode(), 999L, NodeStatus.ACTIVE, 0);
    nodeBucket.put(willNotInsertNode3); // does not replace willInsertNode with worse last retry
    assertTrue(nodeBucket.getNodeRecords().contains(willInsertNode2));
    assertTrue(nodeBucket.getNodeRecords().contains(top));

    NodeRecordInfo willInsertNodeDead =
        new NodeRecordInfo(willInsertNode.getNode(), 1001L, NodeStatus.DEAD, 0);
    nodeBucket.put(willInsertNodeDead); // removes willInsertNode
    assertEquals(NodeBucket.K - 1, nodeBucket.size());
    assertFalse(nodeBucket.contains(willInsertNode2));
  }

  @Test
  public void testStorage() {
    NodeRecordInfo initial = generateUniqueRecord(0);
    Database database = Database.inMemoryDB();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeBucketStorage nodeBucketStorage =
        nodeTableStorageFactory.createBucketStorage(database, TEST_SERIALIZER, initial.getNode());

    int j = 1;
    for (int i = 0; i < 20; ) {
      NodeRecordInfo nodeRecordInfo = generateUniqueRecord(j);
      if (Functions.logDistance(initial.getNode().getNodeId(), nodeRecordInfo.getNode().getNodeId())
          == 255) {
        nodeBucketStorage.put(nodeRecordInfo);
        ++i;
      }
      ++j;
    }
    for (int i = 0; i < 3; ) {
      NodeRecordInfo nodeRecordInfo = generateUniqueRecord(j);
      if (Functions.logDistance(initial.getNode().getNodeId(), nodeRecordInfo.getNode().getNodeId())
          == 254) {
        nodeBucketStorage.put(nodeRecordInfo);
        ++i;
      }
      ++j;
    }
    assertEquals(16, nodeBucketStorage.get(255).get().size());
    assertEquals(3, nodeBucketStorage.get(254).get().size());
    assertFalse(nodeBucketStorage.get(253).isPresent());
    assertFalse(nodeBucketStorage.get(256).isPresent());
  }
}
