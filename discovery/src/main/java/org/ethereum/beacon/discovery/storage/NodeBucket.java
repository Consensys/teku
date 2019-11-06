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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * Storage for nodes, K-Bucket. Holds only {@link #K} nodes, replacing nodes with the same nodeId
 * and nodes with old lastRetry. Also throws out DEAD nodes without taking any notice on other
 * fields.
 */
public class NodeBucket {
  /** Bucket size, number of nodes */
  public static final int K = 16;

  private static final Predicate<NodeRecordInfo> FILTER =
      nodeRecord -> nodeRecord.getStatus().equals(NodeStatus.ACTIVE);
  private static final Comparator<NodeRecordInfo> COMPARATOR =
      (o1, o2) -> {
        if (o1.getNode().getNodeId().equals(o2.getNode().getNodeId())) {
          return 0;
        } else {
          return Long.signum(o1.getLastRetry() - o2.getLastRetry());
        }
      };
  private final TreeSet<NodeRecordInfo> bucket = new TreeSet<>(COMPARATOR);

  public static NodeBucket fromRlpBytes(Bytes bytes, NodeRecordFactory nodeRecordFactory) {
    NodeBucket nodeBucket = new NodeBucket();
    ((RlpList) RlpDecoder.decode(bytes.toArray()).getValues().get(0))
        .getValues().stream()
            .map(rt -> (RlpString) rt)
            .map(RlpString::getBytes)
            .map(Bytes::wrap)
            .map((Bytes bytes1) -> NodeRecordInfo.fromRlpBytes(bytes1, nodeRecordFactory))
            .forEach(nodeBucket::put);
    return nodeBucket;
  }

  public synchronized boolean put(NodeRecordInfo nodeRecord) {
    if (FILTER.test(nodeRecord)) {
      if (!bucket.contains(nodeRecord)) {
        boolean modified = bucket.add(nodeRecord);
        if (bucket.size() > K) {
          bucket.pollFirst();
        }
        return modified;
      } else {
        NodeRecordInfo bucketNode = bucket.subSet(nodeRecord, true, nodeRecord, true).first();
        if (nodeRecord.getLastRetry() > bucketNode.getLastRetry()) {
          bucket.remove(bucketNode);
          bucket.add(nodeRecord);
          return true;
        }
      }
    } else {
      return bucket.remove(nodeRecord);
    }

    return false;
  }

  public boolean contains(NodeRecordInfo nodeRecordInfo) {
    return bucket.contains(nodeRecordInfo);
  }

  public void putAll(Collection<NodeRecordInfo> nodeRecords) {
    nodeRecords.forEach(this::put);
  }

  public synchronized Bytes toRlpBytes() {
    byte[] res =
        RlpEncoder.encode(
            new RlpList(
                bucket.stream()
                    .map(NodeRecordInfo::toRlpBytes)
                    .map(Bytes::toArray)
                    .map(RlpString::create)
                    .collect(Collectors.toList())));
    return Bytes.wrap(res);
  }

  public int size() {
    return bucket.size();
  }

  public List<NodeRecordInfo> getNodeRecords() {
    return new ArrayList<>(bucket);
  }
}
