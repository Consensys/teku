package tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container14;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema14;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

public class ExecutionPayloadBellatrix
    extends Container14<
        ExecutionPayloadBellatrix,
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
        SszList<Transaction>>
    implements ExecutionPayload {

  public ExecutionPayloadBellatrix(
      ContainerSchema14<
              ExecutionPayloadBellatrix,
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
              SszList<Transaction>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayloadBellatrix(
      ExecutionPayloadSchemaBellatrix schema,
      SszBytes32 parentHash,
      SszByteVector feeRecipient,
      SszBytes32 stateRoot,
      SszBytes32 receiptsRoot,
      SszByteVector logsBloom,
      SszBytes32 random,
      SszUInt64 blockNumber,
      SszUInt64 gasLimit,
      SszUInt64 gasUsed,
      SszUInt64 timestamp,
      SszByteList extraData,
      SszUInt256 baseFeePerGas,
      SszBytes32 blockHash,
      SszList<Transaction> transactions) {
    super(
        schema,
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        random,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash,
        transactions);
  }

  @Override
  public boolean isDefaultPayload() {
    return super.isDefault();
  }

  @Override
  public ExecutionPayloadSchemaBellatrix getSchema() {
    return (ExecutionPayloadSchemaBellatrix) super.getSchema();
  }

  @Override
  public Bytes32 getParentHash() {
    return getField0().get();
  }

  @Override
  public Bytes20 getFeeRecipient() {
    return Bytes20.leftPad(getField1().getBytes());
  }

  @Override
  public Bytes32 getStateRoot() {
    return getField2().get();
  }

  @Override
  public Bytes32 getReceiptsRoot() {
    return getField3().get();
  }

  @Override
  public Bytes getLogsBloom() {
    return getField4().getBytes();
  }

  @Override
  public Bytes32 getPrevRandao() {
    return getField5().get();
  }

  @Override
  public UInt64 getBlockNumber() {
    return getField6().get();
  }

  @Override
  public UInt64 getGasLimit() {
    return getField7().get();
  }

  @Override
  public UInt64 getGasUsed() {
    return getField8().get();
  }

  @Override
  public UInt64 getTimestamp() {
    return getField9().get();
  }

  @Override
  public Bytes getExtraData() {
    return getField10().getBytes();
  }

  @Override
  public UInt256 getBaseFeePerGas() {
    return getField11().get();
  }

  @Override
  public Bytes32 getBlockHash() {
    return getField12().get();
  }

  @Override
  public Bytes32 getPayloadHash() {
    return hashTreeRoot();
  }

  @Override
  public SszList<Transaction> getTransactions() {
    return getField13();
  }

  @Override
  public TreeNode getUnblindedNode() {
    return getTransactions().getBackingNode();
  }

  @Override
  public Optional<ExecutionPayloadBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}
