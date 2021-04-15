package tech.pegasys.teku.spec.datastructures.execution;

import static tech.pegasys.teku.spec.config.SpecConfig.BYTES_PER_LOGS_BLOOM;

import java.util.function.Consumer;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.ssz.SSZTypes.Bytes20;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.ContainerSchema11;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class ExecutionPayloadSchema
    extends ContainerSchema11<
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

  public static ExecutionPayloadSchema create(final SpecConfig specConfig) {
    SpecConfigMerge specConfigMerge = SpecConfigMerge.required(specConfig);
    return new ExecutionPayloadSchema(
        BYTES_PER_LOGS_BLOOM,
        specConfigMerge.getMaxBytesPerOpaqueTransaction(),
        specConfigMerge.getMaxApplicationTransactions());
  }

  public static ExecutionPayloadSchema create(
      int bytesPerLogsBloom, int maxBytesPerOpaqueTransaction, int maxApplicationTransactions) {
    return new ExecutionPayloadSchema(
        bytesPerLogsBloom, maxBytesPerOpaqueTransaction, maxApplicationTransactions);
  }

  private ExecutionPayloadSchema(
      int bytesPerLogsBloom, int maxBytesPerOpaqueTransaction, int maxApplicationTransactions) {
    super(
        "ExecutionPayload",
        namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("parent_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("coinbase", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("number", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("gas_used", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("timestamp", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("receipt_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("logs_bloom", SszByteVectorSchema.create(bytesPerLogsBloom)),
        namedSchema(
            "transactions",
            SszListSchema.create(
                SszByteListSchema.create(maxBytesPerOpaqueTransaction),
                maxApplicationTransactions)));
  }

  @Override
  public ExecutionPayload createFromBackingNode(TreeNode node) {
    return new ExecutionPayload(this, node);
  }

  public ExecutionPayload createEmpty() {
    return new ExecutionPayload(this);
  }

  public ExecutionPayload create(final Consumer<ExecutionPayloadBuilder> builder) {
    ExecutionPayloadBuilder content = new ExecutionPayloadBuilder().schema(this);
    builder.accept(content);
    return content.build();
  }
}
