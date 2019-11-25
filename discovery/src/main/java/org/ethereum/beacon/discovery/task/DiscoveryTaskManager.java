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

package org.ethereum.beacon.discovery.task;

import static org.ethereum.beacon.discovery.schema.NodeStatus.DEAD;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.util.Functions;

/** Manages recurrent node check task(s) */
public class DiscoveryTaskManager {
  private static final int LIVE_CHECK_DISTANCE = 100;
  private static final int RECURSIVE_LOOKUP_DISTANCE = 100;
  private static final int STATUS_EXPIRATION_SECONDS = 600;
  private static final int LIVE_CHECK_INTERVAL_SECONDS = 1;
  private static final int RECURSIVE_LOOKUP_INTERVAL_SECONDS = 10;
  private static final int RETRY_TIMEOUT_SECONDS = 60;
  private static final int MAX_RETRIES = 10;
  private final Scheduler scheduler;
  private final Bytes homeNodeId;
  private final LiveCheckTasks liveCheckTasks;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final NodeTable nodeTable;
  private final NodeBucketStorage nodeBucketStorage;
  /**
   * Checks whether {@link NodeRecord} is ready for alive status check. Plus, marks records as DEAD
   * if there were a lot of unsuccessful retries to get reply from node.
   *
   * <p>We don't need to recheck the node if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   *   <li>Node is marked as {@link NodeStatus#DEAD}
   *   <li>Node is not ACTIVE but last connection retry was "seconds ago"
   * </ul>
   *
   * <p>In all other cases method returns true, meaning node is ready for ping check
   */
  private final Predicate<NodeRecordInfo> LIVE_CHECK_NODE_RULE =
      nodeRecord -> {
        long currentTime = Functions.getTime();
        if (nodeRecord.getStatus() == NodeStatus.ACTIVE
            && nodeRecord.getLastRetry() > currentTime - STATUS_EXPIRATION_SECONDS) {
          return false; // no need to rediscover
        }
        if (DEAD.equals(nodeRecord.getStatus())) {
          return false; // node looks dead but we are keeping its records for some reason
        }
        if ((currentTime - nodeRecord.getLastRetry())
            < (nodeRecord.getRetry() * nodeRecord.getRetry())) {
          return false; // too early for retry
        }

        return true;
      };

  /**
   * Checks whether {@link org.ethereum.beacon.discovery.schema.NodeRecord} is ready for FINDNODE
   * query which expands the list of all known nodes.
   *
   * <p>Node is eligible if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   * </ul>
   */
  private final Predicate<NodeRecordInfo> RECURSIVE_LOOKUP_NODE_RULE =
      nodeRecord -> {
        long currentTime = Functions.getTime();
        if (nodeRecord.getStatus() == NodeStatus.ACTIVE
            && nodeRecord.getLastRetry() > currentTime - STATUS_EXPIRATION_SECONDS) {
          return true;
        }

        return false;
      };

  /** Checks whether node is eligible to be considered as dead */
  private final Predicate<NodeRecordInfo> DEAD_RULE =
      nodeRecord -> nodeRecord.getRetry() >= MAX_RETRIES;

  private final Consumer<NodeRecord>[] nodeRecordUpdatesConsumers;
  private boolean resetDead;
  private boolean removeDead;

  /**
   * @param discoveryManager Discovery manager
   * @param nodeTable Ethereum node records storage, stores all found nodes
   * @param nodeBucketStorage Node bucket storage. stores only closest nodes in ready-to-answer
   *     format
   * @param homeNode Home node
   * @param scheduler scheduler to run recurrent tasks on
   * @param resetDead Whether to reset dead status of the nodes on start. If set to true, resets its
   *     status at startup and sets number of used retries to 0. Reset applies after remove, so if
   *     remove is on, reset will be applied to 0 nodes
   * @param removeDead Whether to remove nodes that are found dead after several retries
   * @param nodeRecordUpdatesConsumers consumers are executed when nodeRecord is updated with new
   *     sequence number, so it should be updated in nodeSession
   */
  @SuppressWarnings({"unchecked"})
  public DiscoveryTaskManager(
      DiscoveryManager discoveryManager,
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      NodeRecord homeNode,
      Scheduler scheduler,
      boolean resetDead,
      boolean removeDead,
      Consumer<NodeRecord>... nodeRecordUpdatesConsumers) {
    this.scheduler = scheduler;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.homeNodeId = homeNode.getNodeId();
    this.liveCheckTasks =
        new LiveCheckTasks(discoveryManager, scheduler, Duration.ofSeconds(RETRY_TIMEOUT_SECONDS));
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, Duration.ofSeconds(RETRY_TIMEOUT_SECONDS));
    this.resetDead = resetDead;
    this.removeDead = removeDead;
    this.nodeRecordUpdatesConsumers = nodeRecordUpdatesConsumers;
  }

  public void start() {
    scheduler.executeAtFixedRate(
        Duration.ZERO, Duration.ofSeconds(LIVE_CHECK_INTERVAL_SECONDS), this::liveCheckTask);
    scheduler.executeAtFixedRate(
        Duration.ZERO,
        Duration.ofSeconds(RECURSIVE_LOOKUP_INTERVAL_SECONDS),
        this::recursiveLookupTask);
  }

  private void liveCheckTask() {
    List<NodeRecordInfo> nodes = nodeTable.findClosestNodes(homeNodeId, LIVE_CHECK_DISTANCE);

    // Dead nodes handling
    nodes.stream()
        .filter(DEAD_RULE)
        .forEach(
            deadMarkedNode -> {
              if (removeDead) {
                nodeTable.remove(deadMarkedNode);
              } else {
                nodeTable.save(
                    new NodeRecordInfo(
                        deadMarkedNode.getNode(),
                        deadMarkedNode.getLastRetry(),
                        DEAD,
                        deadMarkedNode.getRetry()));
              }
            });

    // resets dead records
    Stream<NodeRecordInfo> closestNodes = nodes.stream();
    if (resetDead) {
      closestNodes =
          closestNodes.map(
              nodeRecordInfo -> {
                if (DEAD.equals(nodeRecordInfo.getStatus())) {
                  return new NodeRecordInfo(
                      nodeRecordInfo.getNode(), nodeRecordInfo.getLastRetry(), NodeStatus.SLEEP, 0);
                } else {
                  return nodeRecordInfo;
                }
              });
      resetDead = false;
    }

    // Live check task
    closestNodes
        .filter(LIVE_CHECK_NODE_RULE)
        .forEach(
            nodeRecord ->
                liveCheckTasks.add(
                    nodeRecord,
                    () ->
                        updateNode(
                            nodeRecord,
                            new NodeRecordInfo(
                                nodeRecord.getNode(), Functions.getTime(), NodeStatus.ACTIVE, 0)),
                    () ->
                        updateNode(
                            nodeRecord,
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                Functions.getTime(),
                                NodeStatus.SLEEP,
                                (nodeRecord.getRetry() + 1)))));
  }

  private void recursiveLookupTask() {
    List<NodeRecordInfo> nodes = nodeTable.findClosestNodes(homeNodeId, RECURSIVE_LOOKUP_DISTANCE);
    nodes.stream()
        .filter(RECURSIVE_LOOKUP_NODE_RULE)
        .forEach(
            nodeRecord ->
                recursiveLookupTasks.add(
                    nodeRecord,
                    () -> {},
                    () ->
                        updateNode(
                            nodeRecord,
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                Functions.getTime(),
                                NodeStatus.SLEEP,
                                (nodeRecord.getRetry() + 1)))));
  }

  void onNodeRecordUpdate(NodeRecord nodeRecord) {
    for (Consumer<NodeRecord> consumer : nodeRecordUpdatesConsumers) {
      consumer.accept(nodeRecord);
    }
  }

  private void updateNode(NodeRecordInfo oldNodeRecordInfo, NodeRecordInfo newNodeRecordInfo) {
    // use node with latest seq known
    if (newNodeRecordInfo.getNode().getSeq().compareTo(oldNodeRecordInfo.getNode().getSeq()) < 0) {
      newNodeRecordInfo =
          new NodeRecordInfo(
              oldNodeRecordInfo.getNode(),
              newNodeRecordInfo.getLastRetry(),
              newNodeRecordInfo.getStatus(),
              newNodeRecordInfo.getRetry());
    } else {
      onNodeRecordUpdate(newNodeRecordInfo.getNode());
    }
    nodeTable.save(newNodeRecordInfo);
    nodeBucketStorage.put(newNodeRecordInfo);
  }
}
