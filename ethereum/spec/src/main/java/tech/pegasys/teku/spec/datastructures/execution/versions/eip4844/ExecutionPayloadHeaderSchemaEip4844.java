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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXCESS_BLOBS;
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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema16;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapella;

public class ExecutionPayloadHeaderSchemaEip4844
    extends ContainerSchema16<
        ExecutionPayloadHeaderEip4844Impl,
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
        SszUInt64,
        SszBytes32,
        SszBytes32,
        SszBytes32>
    implements ExecutionPayloadHeaderSchema<ExecutionPayloadHeaderEip4844Impl> {

  private final ExecutionPayloadHeaderEip4844Impl defaultExecutionPayloadHeader;
  private final ExecutionPayloadHeaderEip4844Impl executionPayloadHeaderOfDefaultPayload;

  public ExecutionPayloadHeaderSchemaEip4844(final SpecConfigEip4844 specConfig) {
    super(
        "ExecutionPayloadHeaderEip4844",
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
        namedSchema(EXCESS_BLOBS, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(TRANSACTIONS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(WITHDRAWALS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));

    final ExecutionPayloadEip4844Impl defaultExecutionPayload =
        new ExecutionPayloadSchemaEip4844(specConfig).getDefault();

    this.executionPayloadHeaderOfDefaultPayload =
        createFromExecutionPayload(defaultExecutionPayload);

    this.defaultExecutionPayloadHeader = createFromBackingNode(getDefaultTree());
  }

  public ExecutionPayloadHeaderEip4844Impl create(
      Bytes32 parentHash,
      Bytes20 feeRecipient,
      Bytes32 stateRoot,
      Bytes32 receiptsRoot,
      Bytes logsBloom,
      Bytes32 prevRandao,
      UInt64 blockNumber,
      UInt64 gasLimit,
      UInt64 gasUsed,
      UInt64 timestamp,
      Bytes extraData,
      UInt256 baseFeePerGas,
      UInt64 excessBlobs,
      Bytes32 blockHash,
      Bytes32 transactionsRoot,
      Bytes32 withdrawalsRoot) {
    return new ExecutionPayloadHeaderEip4844Impl(
        this,
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
        getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszUInt64.of(excessBlobs),
        SszBytes32.of(blockHash),
        SszBytes32.of(transactionsRoot),
        SszBytes32.of(withdrawalsRoot));
  }

  public ExecutionPayloadHeaderEip4844Impl createFromExecutionPayloadHeaderCapella(
      final ExecutionPayloadHeaderCapella executionPayloadHeaderCapella) {
    return new ExecutionPayloadHeaderEip4844Impl(
        this,
        SszBytes32.of(executionPayloadHeaderCapella.getParentHash()),
        SszByteVector.fromBytes(executionPayloadHeaderCapella.getFeeRecipient().getWrappedBytes()),
        SszBytes32.of(executionPayloadHeaderCapella.getStateRoot()),
        SszBytes32.of(executionPayloadHeaderCapella.getReceiptsRoot()),
        SszByteVector.fromBytes(executionPayloadHeaderCapella.getLogsBloom()),
        SszBytes32.of(executionPayloadHeaderCapella.getPrevRandao()),
        SszUInt64.of(executionPayloadHeaderCapella.getBlockNumber()),
        SszUInt64.of(executionPayloadHeaderCapella.getGasLimit()),
        SszUInt64.of(executionPayloadHeaderCapella.getGasUsed()),
        SszUInt64.of(executionPayloadHeaderCapella.getTimestamp()),
        getExtraDataSchema().fromBytes(executionPayloadHeaderCapella.getExtraData()),
        SszUInt256.of(executionPayloadHeaderCapella.getBaseFeePerGas()),
        SszUInt64.ZERO,
        SszBytes32.of(executionPayloadHeaderCapella.getBlockHash()),
        SszBytes32.of(executionPayloadHeaderCapella.getTransactionsRoot()),
        SszBytes32.of(Bytes32.ZERO));
  }

  private SszByteListSchema<?> getExtraDataSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(EXTRA_DATA));
  }

  @Override
  public long getBlindedNodeGeneralizedIndex() {
    return getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS_ROOT));
  }

  @Override
  public ExecutionPayloadHeaderEip4844Impl getDefault() {
    return defaultExecutionPayloadHeader;
  }

  @Override
  public ExecutionPayloadHeaderEip4844 getHeaderOfDefaultPayload() {
    return executionPayloadHeaderOfDefaultPayload;
  }

  @Override
  public ExecutionPayloadHeaderEip4844Impl createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadHeaderEip4844Impl(this, node);
  }

  @Override
  public ExecutionPayloadHeaderEip4844Impl createFromExecutionPayload(
      final ExecutionPayload payload) {
    final ExecutionPayloadEip4844 executionPayload = ExecutionPayloadEip4844.required(payload);
    return new ExecutionPayloadHeaderEip4844Impl(
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
        SszUInt64.of(executionPayload.getExcessBlobs()),
        SszBytes32.of(executionPayload.getBlockHash()),
        SszBytes32.of(executionPayload.getTransactions().hashTreeRoot()),
        SszBytes32.of(executionPayload.getWithdrawals().hashTreeRoot()));
  }

  @Override
  public Optional<ExecutionPayloadHeaderSchemaEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
