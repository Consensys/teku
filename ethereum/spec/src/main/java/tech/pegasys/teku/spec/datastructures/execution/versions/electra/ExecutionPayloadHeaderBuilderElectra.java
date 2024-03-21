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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderBuilderDeneb;

public class ExecutionPayloadHeaderBuilderElectra extends ExecutionPayloadHeaderBuilderDeneb {
  private ExecutionPayloadHeaderSchemaElectra schema;

  protected Bytes32 depositReceiptsRoot;
  protected Bytes32 exitsRoot;

  public ExecutionPayloadHeaderBuilderElectra schema(
      final ExecutionPayloadHeaderSchemaElectra schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder depositReceiptsRoot(
      final Supplier<Bytes32> depositReceiptsRootSupplier) {
    this.depositReceiptsRoot = depositReceiptsRootSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder exitsRoot(final Supplier<Bytes32> exitsRootSupplier) {
    this.exitsRoot = exitsRootSupplier.get();
    return this;
  }

  @Override
  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(depositReceiptsRoot, "depositReceiptsRoot must be specified");
    checkNotNull(exitsRoot, "exitsRoot must be specified");
  }

  @Override
  public ExecutionPayloadHeader build() {
    validate();
    return new ExecutionPayloadHeaderElectraImpl(
        schema,
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszBytes32.of(receiptsRoot),
        SszByteVector.fromBytes(logsBloom),
        SszBytes32.of(prevRandao),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        schema.getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszBytes32.of(blockHash),
        SszBytes32.of(transactionsRoot),
        SszBytes32.of(withdrawalsRoot),
        SszUInt64.of(blobGasUsed),
        SszUInt64.of(excessBlobGas),
        SszBytes32.of(depositReceiptsRoot),
        SszBytes32.of(exitsRoot));
  }
}
