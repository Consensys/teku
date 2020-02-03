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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.eventbus.EventBus;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryPeer;

@SuppressWarnings("UnstableApiUsage")
public class EventEmittingNodeTable extends DelegatingNodeTable {

  Logger logger = LogManager.getLogger();

  private final EventBus eventBus;

  public EventEmittingNodeTable(final NodeTable delegate, final EventBus eventBus) {
    super(delegate);
    checkNotNull(eventBus, "EventBus cannot be null");
    this.eventBus = eventBus;
  }

  /**
   * Posts a DiscoveryPeer onto the EventBus even if the node is already present in the NodeTable;
   * the NodeTable is left unmodified.
   *
   * @param node
   */
  @Override
  public void save(NodeRecordInfo node) {
    DiscoveryPeer discoveryPeer = DiscoveryPeer.fromNodeRecord(node.getNode());
    super.save(node);
    eventBus.post(discoveryPeer);
    logger.debug(() -> String.format("On %s, Posted saved node: %s", getHomeNode(), node));
    logger.debug(() -> String.format("On %s, %s", getHomeNode(),
        Arrays.stream(Thread.currentThread().getStackTrace()).map(StackTraceElement::toString)
            .collect(
                Collectors.toList())));
  }

  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    List<NodeRecordInfo> closestNodes = super.findClosestNodes(nodeId, logLimit);
    logger.debug(
        "On " + this.getHomeNode().getPort() + ", Closest nodes of " + getHomeNode().getPort() + ":"
            + closestNodes);
    return closestNodes;
  }
}
