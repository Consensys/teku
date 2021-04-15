package tech.pegasys.teku.spec.datastructures.execution;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes20;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.Container11;
import tech.pegasys.teku.ssz.containers.ContainerSchema11;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class ExecutionPayload
    extends Container11<
    ExecutionPayload,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszBytes32,
        SszByteVector,
        SszList<SszByteList>> {

  protected ExecutionPayload(
      ContainerSchema11<
          ExecutionPayload,
              SszBytes32,
              SszBytes32,
              SszByteVector,
              SszBytes32,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszBytes32,
              SszByteVector,
              SszList<SszByteList>>
          type) {
    super(type);
  }

  ExecutionPayload(
      ContainerSchema11<
          ExecutionPayload,
              SszBytes32,
              SszBytes32,
              SszByteVector,
              SszBytes32,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszBytes32,
              SszByteVector,
              SszList<SszByteList>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  ExecutionPayload(
      ContainerSchema11<
          ExecutionPayload,
              SszBytes32,
              SszBytes32,
              SszByteVector,
              SszBytes32,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszBytes32,
              SszByteVector,
              SszList<SszByteList>>
          schema,
      SszBytes32 block_ash,
      SszBytes32 parent_hash,
      SszByteVector coinbase,
      SszBytes32 state_root,
      SszUInt64 number,
      SszUInt64 gas_limit,
      SszUInt64 gas_used,
      SszUInt64 timestamp,
      SszBytes32 receipt_root,
      SszByteVector logs_bloom,
      SszList<SszByteList> transactions) {
    super(
        schema,
        block_ash,
        parent_hash,
        coinbase,
        state_root,
        number,
        gas_limit,
        gas_used,
        timestamp,
        receipt_root,
        logs_bloom,
        transactions);
  }

  @Override
  public ExecutionPayloadSchema getSchema() {
    return (ExecutionPayloadSchema) super.getSchema();
  }

  public Bytes32 getBlock_hash() {
    return getField0().get();
  }

  public Bytes32 getParent_hash() {
    return getField1().get();
  }

  public Bytes20 getCoinbase() {
    return Bytes20.leftPad(getField2().getBytes());
  }

  public Bytes32 getState_root() {
    return getField3().get();
  }

  public UInt64 getNumber() {
    return getField4().get();
  }

  public UInt64 getGas_limit() {
    return getField5().get();
  }

  public UInt64 getGas_used() {
    return getField6().get();
  }

  public UInt64 getTimestamp() {
    return getField7().get();
  }

  public Bytes32 getReceipt_root() {
    return getField8().get();
  }

  public Bytes getLogs_bloom() {
    return getField9().getBytes();
  }

  public List<Bytes> getTransactions() {
    return getField10().stream().map(SszByteList::getBytes).collect(Collectors.toList());
  }
}
