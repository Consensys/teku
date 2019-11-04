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

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.BytesValue;
import org.ethereum.beacon.discovery.DataSource;
import org.ethereum.beacon.discovery.Functions;
import org.ethereum.beacon.discovery.Hash32;
import org.ethereum.beacon.discovery.HoleyList;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.SingleValueSource;
import org.ethereum.beacon.discovery.enr.NodeRecord;

// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.bytes.Bytes;

/**
 * Stores Ethereum Node Records in {@link NodeRecordInfo} containers. Also stores home node as node
 * record. Uses indexes, {@link NodeIndex} for quick access to nodes that are close to others.
 */
public class NodeTableImpl implements NodeTable {
  static final long NUMBER_OF_INDEXES = 256;
  private static final Logger logger = LogManager.getLogger(NodeTableImpl.class);
  private static final int MAXIMUM_INFO_IN_ONE_BYTE = 256;
  private static final boolean START_FROM_BEGINNING = true;
  private final DataSource<Hash32, NodeRecordInfo> nodeTable;
  private final HoleyList<NodeIndex> indexTable;
  private final SingleValueSource<NodeRecordInfo> homeNodeSource;

  public NodeTableImpl(
      DataSource<Hash32, NodeRecordInfo> nodeTable,
      HoleyList<NodeIndex> indexTable,
      SingleValueSource<NodeRecordInfo> homeNodeSource) {
    this.nodeTable = nodeTable;
    this.indexTable = indexTable;
    this.homeNodeSource = homeNodeSource;
  }

  @VisibleForTesting
  static long getNodeIndex(BytesValue nodeKey) {
    int activeBytes = 1;
    long required = NUMBER_OF_INDEXES;
    while (required > 0) {
      if (required == MAXIMUM_INFO_IN_ONE_BYTE) {
        required = 0;
      } else {
        required = required / MAXIMUM_INFO_IN_ONE_BYTE;
      }

      if (required > 0) {
        activeBytes++;
      }
    }

    int start = START_FROM_BEGINNING ? 0 : nodeKey.size() - activeBytes;
    BytesValue active = nodeKey.slice(start, activeBytes);
    BigInteger activeNumber = new BigInteger(1, active.extractArray());
    // XXX: could be optimized for small NUMBER_OF_INDEXES
    BigInteger index = activeNumber.mod(BigInteger.valueOf(NUMBER_OF_INDEXES));

    return index.longValue();
  }

  @Override
  public void save(NodeRecordInfo node) {
    Hash32 nodeKey = Hash32.wrap(BytesValue.wrap(node.getNode().getNodeId().toArray()));
    nodeTable.put(nodeKey, node);
    NodeIndex activeIndex = indexTable.get(getNodeIndex(nodeKey)).orElseGet(NodeIndex::new);
    List<Hash32> nodes = activeIndex.getEntries();
    if (!nodes.contains(nodeKey)) {
      nodes.add(nodeKey);
      indexTable.put(getNodeIndex(nodeKey), activeIndex);
    }
  }

  @Override
  public void remove(NodeRecordInfo node) {
    Hash32 nodeKey = Hash32.wrap(BytesValue.wrap(node.getNode().getNodeId().toArray()));
    nodeTable.remove(nodeKey);
    NodeIndex activeIndex = indexTable.get(getNodeIndex(nodeKey)).orElseGet(NodeIndex::new);
    List<Hash32> nodes = activeIndex.getEntries();
    if (nodes.contains(nodeKey)) {
      nodes.remove(nodeKey);
      indexTable.put(getNodeIndex(nodeKey), activeIndex);
    }
  }

  @Override
  public Optional<NodeRecordInfo> getNode(Bytes nodeId) {
    return nodeTable.get(Hash32.wrap(BytesValue.wrap(nodeId.toArray())));
  }

  /**
   * Returns list of nodes including `nodeId` (if it's found) in logLimit distance from it. Uses
   * {@link Functions#logDistance(Bytes, Bytes)} as distance function.
   */
  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    long start = getNodeIndex(BytesValue.wrap(nodeId.toArray()));
    boolean limitReached = false;
    long currentIndexUp = start;
    long currentIndexDown = start;
    Set<NodeRecordInfo> res = new HashSet<>();
    while (!limitReached) {
      Optional<NodeIndex> upNodesOptional =
          currentIndexUp >= NUMBER_OF_INDEXES ? Optional.empty() : indexTable.get(currentIndexUp);
      Optional<NodeIndex> downNodesOptional =
          currentIndexDown < 0 ? Optional.empty() : indexTable.get(currentIndexDown);
      if (currentIndexUp >= NUMBER_OF_INDEXES && currentIndexDown < 0) {
        // Bounds are reached from both top and bottom
        break;
      }
      if (upNodesOptional.isPresent()) {
        NodeIndex upNodes = upNodesOptional.get();
        for (Hash32 currentNodeId : upNodes.getEntries()) {
          if (Functions.logDistance(Bytes.wrap(currentNodeId.extractArray()), nodeId) >= logLimit) {
            limitReached = true;
            break;
          } else {
            res.add(getNode(Bytes.wrap(currentNodeId.extractArray())).get());
          }
        }
      }
      if (downNodesOptional.isPresent()) {
        NodeIndex downNodes = downNodesOptional.get();
        List<Hash32> entries = downNodes.getEntries();
        // XXX: iterate in reverse order to reach logDistance limit from the right side
        for (int i = entries.size() - 1; i >= 0; i--) {
          Hash32 currentNodeId = entries.get(i);
          if (Functions.logDistance(Bytes.wrap(currentNodeId.extractArray()), nodeId) >= logLimit) {
            limitReached = true;
            break;
          } else {
            res.add(getNode(Bytes.wrap(currentNodeId.extractArray())).get());
          }
        }
      }
      currentIndexUp++;
      currentIndexDown--;
    }

    return new ArrayList<>(res);
  }

  @Override
  public NodeRecord getHomeNode() {
    return homeNodeSource.get().map(NodeRecordInfo::getNode).orElse(null);
  }
}
