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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.DATA_GAS_USED;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXCESS_DATA_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXTRA_DATA;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.FEE_RECIPIENT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_LIMIT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_USED;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.LOGS_BLOOM;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PREV_RANDAO;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.RECEIPTS_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.STATE_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.TIMESTAMP;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.TRANSACTIONS_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.WITHDRAWALS_ROOT;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema17;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class ExecutionPayloadHeaderSchemaDeneb
    extends ContainerSchema17<
        ExecutionPayloadHeaderDenebImpl,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszByteList,
        SszUInt256,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszUInt64,
        SszUInt64>
    implements ExecutionPayloadHeaderSchema<ExecutionPayloadHeaderDenebImpl> {

  private final ExecutionPayloadHeaderDenebImpl defaultExecutionPayloadHeader;
  private final ExecutionPayloadHeaderDenebImpl executionPayloadHeaderOfDefaultPayload;

  public ExecutionPayloadHeaderSchemaDeneb(final SpecConfigDeneb specConfig) {
    super(
        "ExecutionPayloadHeaderDeneb",
        namedSchema(PARENT_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(FEE_RECIPIENT, SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema(STATE_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(RECEIPTS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(LOGS_BLOOM, SszByteVectorSchema.create(specConfig.getBytesPerLogsBloom())),
        namedSchema(PREV_RANDAO, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BLOCK_NUMBER, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(GAS_LIMIT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(GAS_USED, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(TIMESTAMP, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(EXTRA_DATA, SszByteListSchema.create(specConfig.getMaxExtraDataBytes())),
        namedSchema(BASE_FEE_PER_GAS, SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(TRANSACTIONS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(WITHDRAWALS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(DATA_GAS_USED, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(EXCESS_DATA_GAS, SszPrimitiveSchemas.UINT64_SCHEMA));

    final ExecutionPayloadDenebImpl defaultExecutionPayload =
        new ExecutionPayloadSchemaDeneb(specConfig).getDefault();

    this.executionPayloadHeaderOfDefaultPayload =
        createFromExecutionPayload(defaultExecutionPayload);

    this.defaultExecutionPayloadHeader = createFromBackingNode(getDefaultTree());
  }

  public SszByteListSchema<?> getExtraDataSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(EXTRA_DATA));
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of(
        getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS_ROOT)),
        getChildGeneralizedIndex(getFieldIndex(WITHDRAWALS_ROOT)));
  }

  @Override
  public ExecutionPayloadHeader createExecutionPayloadHeader(
      final Consumer<ExecutionPayloadHeaderBuilder> builderConsumer) {
    final ExecutionPayloadHeaderBuilderDeneb builder =
        new ExecutionPayloadHeaderBuilderDeneb().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public ExecutionPayloadHeaderDenebImpl getDefault() {
    return defaultExecutionPayloadHeader;
  }

  @Override
  public ExecutionPayloadHeaderDeneb getHeaderOfDefaultPayload() {
    return executionPayloadHeaderOfDefaultPayload;
  }

  @Override
  public ExecutionPayloadHeaderDenebImpl createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadHeaderDenebImpl(this, node);
  }

  @Override
  public ExecutionPayloadHeaderDenebImpl createFromExecutionPayload(
      final ExecutionPayload payload) {
    final ExecutionPayloadDeneb executionPayload = ExecutionPayloadDeneb.required(payload);
    return new ExecutionPayloadHeaderDenebImpl(
        this,
        SszBytes32.of(executionPayload.getParentHash()),
        SszByteVector.fromBytes(executionPayload.getFeeRecipient().getWrappedBytes()),
        SszBytes32.of(executionPayload.getStateRoot()),
        SszBytes32.of(executionPayload.getReceiptsRoot()),
        SszByteVector.fromBytes(executionPayload.getLogsBloom()),
        SszBytes32.of(executionPayload.getPrevRandao()),
        SszUInt64.of(executionPayload.getBlockNumber()),
        SszUInt64.of(executionPayload.getGasLimit()),
        SszUInt64.of(executionPayload.getGasUsed()),
        SszUInt64.of(executionPayload.getTimestamp()),
        getExtraDataSchema().fromBytes(executionPayload.getExtraData()),
        SszUInt256.of(executionPayload.getBaseFeePerGas()),
        SszBytes32.of(executionPayload.getBlockHash()),
        SszBytes32.of(executionPayload.getTransactions().hashTreeRoot()),
        SszBytes32.of(executionPayload.getWithdrawals().hashTreeRoot()),
        SszUInt64.of(executionPayload.getDataGasUsed()),
        SszUInt64.of(executionPayload.getExcessDataGas()));
  }
}
