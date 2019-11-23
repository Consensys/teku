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
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

public class GeneralRequestInfo implements RequestInfo {
  private final TaskType taskType;
  private final TaskStatus taskStatus;
  private final Bytes requestId;
  private final CompletableFuture<Void> future;

  public GeneralRequestInfo(
      TaskType taskType, TaskStatus taskStatus, Bytes requestId, CompletableFuture<Void> future) {
    this.taskType = taskType;
    this.taskStatus = taskStatus;
    this.requestId = requestId;
    this.future = future;
  }

  @Override
  public TaskType getTaskType() {
    return taskType;
  }

  @Override
  public TaskStatus getTaskStatus() {
    return taskStatus;
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  @Override
  public CompletableFuture<Void> getFuture() {
    return future;
  }

  @Override
  public String toString() {
    return "GeneralRequestInfo{"
        + "taskType="
        + taskType
        + ", taskStatus="
        + taskStatus
        + ", requestId="
        + requestId
        + '}';
  }
}
