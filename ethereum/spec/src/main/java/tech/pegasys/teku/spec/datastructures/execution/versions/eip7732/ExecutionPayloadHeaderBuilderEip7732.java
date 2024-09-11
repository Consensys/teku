/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip7732;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadHeaderBuilderElectra;

public class ExecutionPayloadHeaderBuilderEip7732 extends ExecutionPayloadHeaderBuilderElectra {
  private ExecutionPayloadHeaderSchemaEip7732 schema;

  protected Bytes32 parentBlockHash;
  protected Bytes32 parentBlockRoot;
  protected UInt64 builderIndex;
  protected UInt64 slot;
  protected UInt64 value;
  protected Bytes32 blobKzgCommitmentsRoot;

  public ExecutionPayloadHeaderBuilderEip7732 schema(
      final ExecutionPayloadHeaderSchemaEip7732 schema) {
    this.schema = schema;
    return this;
  }

  @Override
  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }

  @Override
  protected void validate() {
    // skipping super.validate() because fields were removed
    // old fields
    checkNotNull(blockHash, "blockHash must be specified");
    checkNotNull(gasLimit, "gasLimit must be specified");
    // new fields
    checkNotNull(parentBlockHash, "parentBlockHash must be specified");
    checkNotNull(parentBlockRoot, "parentBlockRoot must be specified");
    checkNotNull(builderIndex, "builderIndex must be specified");
    checkNotNull(slot, "slot must be specified");
    checkNotNull(value, "value must be specified");
    checkNotNull(blobKzgCommitmentsRoot, "blobKzgCommitmentsRoot must be specified");
    validateSchema();
  }

  @Override
  public ExecutionPayloadHeaderBuilder parentBlockHash(
      final Supplier<Bytes32> parentBlockHashSupplier) {
    this.parentBlockHash = parentBlockHashSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder parentBlockRoot(
      final Supplier<Bytes32> parentBlockRootSupplier) {
    this.parentBlockRoot = parentBlockRootSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder builderIndex(final Supplier<UInt64> builderIndexSupplier) {
    this.builderIndex = builderIndexSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder slot(final Supplier<UInt64> slotSupplier) {
    this.slot = slotSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder value(final Supplier<UInt64> valueSupplier) {
    this.value = valueSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder blobKzgCommitmentsRoot(
      final Supplier<Bytes32> blobKzgCommitmentsRootSupplier) {
    this.blobKzgCommitmentsRoot = blobKzgCommitmentsRootSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeader build() {
    validate();
    return new ExecutionPayloadHeaderEip7732Impl(
        schema,
        SszBytes32.of(parentBlockHash),
        SszBytes32.of(parentBlockRoot),
        SszBytes32.of(blockHash),
        SszUInt64.of(gasLimit),
        SszUInt64.of(builderIndex),
        SszUInt64.of(slot),
        SszUInt64.of(value),
        SszBytes32.of(blobKzgCommitmentsRoot));
  }
}
