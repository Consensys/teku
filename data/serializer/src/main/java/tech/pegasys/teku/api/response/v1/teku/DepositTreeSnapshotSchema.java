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

package tech.pegasys.teku.api.response.v1.teku;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositTreeSnapshotSchema {
  @JsonProperty("finalized")
  @ArraySchema(
      schema = @Schema(type = "string", example = EXAMPLE_BYTES32, pattern = PATTERN_BYTES32),
      arraySchema = @Schema(description = "List of finalized nodes in deposit tree"))
  public List<Bytes32> finalized;

  @JsonProperty("deposit_root")
  @Schema(
      type = "string",
      example = EXAMPLE_BYTES32,
      pattern = PATTERN_BYTES32,
      description = "Root of finalized deposits")
  public Bytes32 depositRoot;

  @JsonProperty("deposit_count")
  @Schema(
      type = "string",
      format = "uint64",
      example = "1",
      description = "Number of deposits stored in the snapshot")
  public UInt64 depositCount;

  @JsonProperty("execution_block_hash")
  @Schema(
      type = "string",
      example = EXAMPLE_BYTES32,
      pattern = PATTERN_BYTES32,
      description =
          "Hash of the execution block containing the highest index deposit stored in the snapshot")
  public Bytes32 executionBlockHash;

  @JsonProperty("execution_block_height")
  @Schema(
      type = "string",
      format = "uint64",
      example = "1",
      description =
          "Height of the execution block in canonical chain containing the highest index deposit stored in the snapshot")
  public UInt64 executionBlockHeight;

  @JsonCreator
  public DepositTreeSnapshotSchema(
      @JsonProperty("finalized") final List<Bytes32> finalized,
      @JsonProperty("deposit_root") final Bytes32 depositRoot,
      @JsonProperty("deposit_count") final UInt64 depositCount,
      @JsonProperty("execution_block_hash") final Bytes32 executionBlockHash,
      @JsonProperty("execution_block_height") final UInt64 executionBlockHeight) {
    this.finalized = finalized;
    this.depositRoot = depositRoot;
    this.depositCount = depositCount;
    this.executionBlockHash = executionBlockHash;
    this.executionBlockHeight = executionBlockHeight;
  }
}
