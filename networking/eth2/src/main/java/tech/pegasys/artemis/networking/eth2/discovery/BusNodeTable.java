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

package tech.pegasys.artemis.networking.eth2.discovery;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.eventbus.EventBus;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;

public class BusNodeTable extends DelegatingNodeTable {

  Logger logger = LogManager.getLogger();

  private final EventBus eventBus;

  public BusNodeTable(final NodeTable delegate, final EventBus eventBus) {
    super(delegate);
    checkNotNull(eventBus, "EventBus cannot be null");
    this.eventBus = eventBus;
  }

  @Override
  public void save(NodeRecordInfo node) {
    super.save(node);
    eventBus.post(new DiscoveryNewPeerResponse(node));
    logger.debug("Posted saved node:" + node);
  }

  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    List<NodeRecordInfo> closestNodes = super.findClosestNodes(nodeId, logLimit);
    eventBus.post(new DiscoveryFindNodesResponse(closestNodes));
    logger.debug("Found closest nodes:" + closestNodes);
    return closestNodes;
  }
}
