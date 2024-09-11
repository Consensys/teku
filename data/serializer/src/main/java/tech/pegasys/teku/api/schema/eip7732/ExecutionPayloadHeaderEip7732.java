/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.api.schema.eip7732;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.ExecutionPayloadHeader;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class ExecutionPayloadHeaderEip7732 implements ExecutionPayloadHeader {

  @JsonProperty("parent_block_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 parentBlockHash;

  @JsonProperty("parent_block_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 parentBlockRoot;

  @JsonProperty("block_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 blockHash;

  @JsonProperty("gas_limit")
  @Schema(type = "string", format = "uint64")
  public final UInt64 gasLimit;

  @JsonProperty("builder_index")
  @Schema(type = "string", format = "uint64")
  public final UInt64 builderIndex;

  @JsonProperty("slot")
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @JsonProperty("value")
  @Schema(type = "string", format = "uint64")
  public final UInt64 value;

  @JsonProperty("blob_kzg_commitments_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 blobKzgCommitmentsRoot;

  @JsonCreator
  public ExecutionPayloadHeaderEip7732(
      final @JsonProperty("parent_block_hash") Bytes32 parentBlockHash,
      final @JsonProperty("parent_block_root") Bytes32 parentBlockRoot,
      final @JsonProperty("block_hash") Bytes32 blockHash,
      final @JsonProperty("gas_limit") UInt64 gasLimit,
      final @JsonProperty("builder_index") UInt64 builderIndex,
      final @JsonProperty("slot") UInt64 slot,
      final @JsonProperty("value") UInt64 value,
      final @JsonProperty("blob_kzg_commitments_root") Bytes32 blobKzgCommitmentsRoot) {
    this.parentBlockHash = parentBlockHash;
    this.parentBlockRoot = parentBlockRoot;
    this.blockHash = blockHash;
    this.gasLimit = gasLimit;
    this.builderIndex = builderIndex;
    this.slot = slot;
    this.value = value;
    this.blobKzgCommitmentsRoot = blobKzgCommitmentsRoot;
  }

  public ExecutionPayloadHeaderEip7732(
      final tech.pegasys.teku.spec.datastructures.execution.versions.eip7732
              .ExecutionPayloadHeaderEip7732
          executionPayloadHeader) {
    this(
        executionPayloadHeader.getParentBlockHash(),
        executionPayloadHeader.getParentBlockRoot(),
        executionPayloadHeader.getBlockHash(),
        executionPayloadHeader.getGasLimit(),
        executionPayloadHeader.getBuilderIndex(),
        executionPayloadHeader.getSlot(),
        executionPayloadHeader.getValue(),
        executionPayloadHeader.getBlobKzgCommitmentsRoot());
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader
      asInternalExecutionPayloadHeader(final ExecutionPayloadHeaderSchema<?> schema) {
    return schema.createExecutionPayloadHeader(
        payloadBuilder ->
            payloadBuilder
                .parentBlockHash(() -> parentBlockHash)
                .parentBlockRoot(() -> parentBlockRoot)
                .blockHash(blockHash)
                .gasLimit(gasLimit)
                .builderIndex(() -> builderIndex)
                .slot(() -> slot)
                .value(() -> value)
                .blobKzgCommitmentsRoot(() -> blobKzgCommitmentsRoot));
  }

  @Override
  public Optional<ExecutionPayloadHeaderEip7732> toVersionEip7732() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof final ExecutionPayloadHeaderEip7732 that)) {
      return false;
    }
    return Objects.equals(parentBlockHash, that.parentBlockHash)
        && Objects.equals(parentBlockRoot, that.parentBlockRoot)
        && Objects.equals(blockHash, that.blockHash)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(builderIndex, that.builderIndex)
        && Objects.equals(slot, that.slot)
        && Objects.equals(value, that.value)
        && Objects.equals(blobKzgCommitmentsRoot, that.blobKzgCommitmentsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        parentBlockHash,
        parentBlockRoot,
        blockHash,
        gasLimit,
        builderIndex,
        slot,
        value,
        blobKzgCommitmentsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parentBlockHash", parentBlockHash)
        .add("parentBlockRoot", parentBlockRoot)
        .add("blockHash", blockHash)
        .add("gasLimit", gasLimit)
        .add("builderIndex", builderIndex)
        .add("slot", slot)
        .add("value", value)
        .add("blobKzgCommitmentsRoot", blobKzgCommitmentsRoot)
        .toString();
  }
}
