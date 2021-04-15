package tech.pegasys.teku.spec.datastructures.execution;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayloadBuilder {
  private Bytes32 blockHash;
  private Bytes32 parentHash;
  private Bytes20 coinbase;
  private Bytes32 stateRoot;
  private UInt64 number;
  private UInt64 gasLimit;
  private UInt64 gasUsed;
  private UInt64 timestamp;
  private Bytes32 receiptRoot;
  private Bytes logsBloom;
  private SszList<SszByteList> transactions;

  private ExecutionPayloadSchema schema;

  public ExecutionPayloadBuilder blockHash(Bytes32 blockHash) {
    this.blockHash = blockHash;
    return this;
  }

  public ExecutionPayloadBuilder parentHash(Bytes32 parentHash) {
    this.parentHash = parentHash;
    return this;
  }

  public ExecutionPayloadBuilder coinbase(Bytes20 coinbase) {
    this.coinbase = coinbase;
    return this;
  }

  public ExecutionPayloadBuilder stateRoot(Bytes32 stateRoot) {
    this.stateRoot = stateRoot;
    return this;
  }

  public ExecutionPayloadBuilder number(UInt64 number) {
    this.number = number;
    return this;
  }

  public ExecutionPayloadBuilder gasLimit(UInt64 gasLimit) {
    this.gasLimit = gasLimit;
    return this;
  }

  public ExecutionPayloadBuilder gasUsed(UInt64 gasUsed) {
    this.gasUsed = gasUsed;
    return this;
  }

  public ExecutionPayloadBuilder timestamp(UInt64 timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public ExecutionPayloadBuilder receiptRoot(Bytes32 receiptRoot) {
    this.receiptRoot = receiptRoot;
    return this;
  }

  public ExecutionPayloadBuilder logsBloom(Bytes logsBloom) {
    this.logsBloom = logsBloom;
    return this;
  }

  public ExecutionPayloadBuilder transactions(SszList<SszByteList> transactions) {
    this.transactions = transactions;
    return this;
  }

  public ExecutionPayloadBuilder transactions(List<Bytes> transactions) {
    this.transactions =
        transactions.stream()
            .map(tx -> schema.getTransactionSchema().fromBytes(tx))
            .collect(schema.getTransactionsSchema().collector());
    return this;
  }

  public ExecutionPayloadBuilder schema(ExecutionPayloadSchema schema) {
    this.schema = schema;
    return this;
  }

  public ExecutionPayload build() {
    validate();
    return new ExecutionPayload(
        schema,
        SszBytes32.of(blockHash),
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(coinbase.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszUInt64.of(number),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        SszBytes32.of(receiptRoot),
        SszByteVector.fromBytes(logsBloom),
        transactions);
  }

  protected void validate() {
    checkNotNull(blockHash, "blockHash must be specified");
    checkNotNull(parentHash, "parentHash must be specified");
    checkNotNull(coinbase, "coinbase must be specified");
    checkNotNull(stateRoot, "stateRoot must be specified");
    checkNotNull(number, "number must be specified");
    checkNotNull(gasLimit, "gasLimit must be specified");
    checkNotNull(gasUsed, "gasUsed must be specified");
    checkNotNull(timestamp, "timestamp must be specified");
    checkNotNull(receiptRoot, "receiptRoot must be specified");
    checkNotNull(logsBloom, "logsBloom must be specified");
    checkNotNull(transactions, "transactions must be specified");
    checkNotNull(schema, "schema must be specified");
  }
}
