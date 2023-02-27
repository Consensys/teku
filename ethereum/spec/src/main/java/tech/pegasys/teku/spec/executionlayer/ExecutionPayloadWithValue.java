/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.executionlayer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class ExecutionPayloadWithValue {
  private final ExecutionPayload executionPayload;
  private final UInt256 value;

  public ExecutionPayloadWithValue(final ExecutionPayload executionPayload, final UInt256 value) {
    this.executionPayload = executionPayload;
    this.value = value;
  }

  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  public UInt256 getValue() {
    return value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadWithValue that = (ExecutionPayloadWithValue) o;
    return Objects.equals(executionPayload, that.executionPayload)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayload, value);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayload", executionPayload)
        .add("value", value)
        .toString();
  }
}
