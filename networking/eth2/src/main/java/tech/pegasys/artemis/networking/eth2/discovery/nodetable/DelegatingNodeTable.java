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

package tech.pegasys.artemis.networking.eth2.discovery.nodetable;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;

public class DelegatingNodeTable implements NodeTable {

  private final NodeTable delegate;

  public DelegatingNodeTable(NodeTable delegate) {
    this.delegate = delegate;
  }

  @Override
  public void save(NodeRecordInfo node) {
    delegate.save(node);
  }

  @Override
  public void remove(NodeRecordInfo node) {
    delegate.remove(node);
  }

  @Override
  public Optional<NodeRecordInfo> getNode(Bytes nodeId) {
    return delegate.getNode(nodeId);
  }

  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    return delegate.findClosestNodes(nodeId, logLimit);
  }

  @Override
  public NodeRecord getHomeNode() {
    return delegate.getHomeNode();
  }
}
