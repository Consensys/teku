/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.services.powchain.execution.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ExecutionPayloadStatus;

public class ExecutePayloadResponse {

  private ExecutionPayloadStatus status;

  public ExecutePayloadResponse(@JsonProperty("status") String status) {
    try {
      ExecutionPayloadStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Invalid status field received: " + status);
    }
  }

  public ExecutionPayloadStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExecutePayloadResponse that = (ExecutePayloadResponse) o;
    return Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status);
  }

  @Override
  public String toString() {
    return "NewBlockResponse{" + "valid=" + status + '}';
  }
}
