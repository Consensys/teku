/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix;

import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;

public class GetPayloadResponseBellatrix implements GetPayloadResponse {

  private final ExecutionPayload executionPayload;

  public GetPayloadResponseBellatrix(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
  }

  @Override
  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetPayloadResponseBellatrix that = (GetPayloadResponseBellatrix) o;
    return Objects.equals(executionPayload, that.executionPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayload);
  }
}
