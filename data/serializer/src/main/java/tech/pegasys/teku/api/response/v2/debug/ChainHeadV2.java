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

package tech.pegasys.teku.api.response.v2.debug;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class ChainHeadV2 {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 root;

  @JsonProperty("execution_optimistic")
  public final boolean execution_optimistic;

  @JsonCreator
  public ChainHeadV2(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("root") final Bytes32 root,
      @JsonProperty("execution_optimistic") final boolean executionOptimistic) {
    this.slot = slot;
    this.root = root;
    this.execution_optimistic = executionOptimistic;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ChainHeadV2 that = (ChainHeadV2) o;
    return execution_optimistic == that.execution_optimistic
        && Objects.equals(slot, that.slot)
        && Objects.equals(root, that.root);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, root, execution_optimistic);
  }
}
