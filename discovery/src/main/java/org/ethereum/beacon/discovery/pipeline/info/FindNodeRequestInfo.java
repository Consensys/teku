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

package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

public class FindNodeRequestInfo extends GeneralRequestInfo {
  private final Integer remainingNodes;
  private final int distance;

  public FindNodeRequestInfo(
      TaskStatus taskStatus,
      Bytes requestId,
      CompletableFuture<Void> future,
      int distance,
      @Nullable Integer remainingNodes) {
    super(TaskType.FINDNODE, taskStatus, requestId, future);
    this.distance = distance;
    this.remainingNodes = remainingNodes;
  }

  public int getDistance() {
    return distance;
  }

  public Integer getRemainingNodes() {
    return remainingNodes;
  }

  @Override
  public String toString() {
    return "FindNodeRequestInfo{"
        + "remainingNodes="
        + remainingNodes
        + ", distance="
        + distance
        + '}';
  }
}
