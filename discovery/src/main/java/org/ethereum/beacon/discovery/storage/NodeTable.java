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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/**
 * Stores Ethereum Node Records in {@link NodeRecordInfo} containers. Also stores home node as node
 * record.
 */
public interface NodeTable {
  void save(NodeRecordInfo node);

  void remove(NodeRecordInfo node);

  Optional<NodeRecordInfo> getNode(Bytes nodeId);

  /** Returns list of nodes including `nodeId` (if it's found) in logLimit distance from it. */
  List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit);

  NodeRecord getHomeNode();
}
