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

import static org.ethereum.beacon.discovery.task.TaskStatus.AWAIT;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskOptions;
import org.ethereum.beacon.discovery.task.TaskType;

public class RequestInfoFactory {
  public static RequestInfo create(
      TaskType taskType, Bytes id, TaskOptions taskOptions, CompletableFuture<Void> future) {
    switch (taskType) {
      case FINDNODE:
        {
          return new FindNodeRequestInfo(AWAIT, id, future, taskOptions.getDistance(), null);
        }
      case PING:
        {
          return new GeneralRequestInfo(taskType, AWAIT, id, future);
        }
      default:
        {
          throw new RuntimeException(
              String.format("Factory doesn't know how to create task with type %s", taskType));
        }
    }
  }
}
