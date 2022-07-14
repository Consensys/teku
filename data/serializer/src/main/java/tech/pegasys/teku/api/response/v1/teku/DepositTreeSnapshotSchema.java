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

  @JsonProperty("deposits")
  @Schema(
      type = "string",
      format = "uint64",
      example = "1",
      description = "Number of deposits stored in the snapshot")
  public UInt64 deposits;

  @JsonProperty("execution_block_hash")
  @Schema(
      type = "string",
      example = EXAMPLE_BYTES32,
      pattern = PATTERN_BYTES32,
      description =
          "Hash of the execution block containing the highest index deposit stored in the snapshot")
  public Bytes32 executionBlockHash;

  @JsonCreator
  public DepositTreeSnapshotSchema(
      @JsonProperty("finalized") final List<Bytes32> finalized,
      @JsonProperty("deposits") final UInt64 deposits,
      @JsonProperty("execution_block_hash") final Bytes32 executionBlockHash) {
    this.finalized = finalized;
    this.deposits = deposits;
    this.executionBlockHash = executionBlockHash;
  }
}
