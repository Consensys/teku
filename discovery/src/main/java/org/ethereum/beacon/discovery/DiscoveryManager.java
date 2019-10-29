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

package org.ethereum.beacon.discovery;

import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.task.TaskType;

/**
 * Discovery Manager, top interface for peer discovery mechanism as described at <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5.md">https://github.com/ethereum/devp2p/blob/master/discv5/discv5.md</a>
 */
public interface DiscoveryManager {
  void start();

  void stop();

  /**
   * Initiates task of task type with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @param taskType Task type
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange.
   */
  CompletableFuture<Void> executeTask(NodeRecord nodeRecord, TaskType taskType);
}
