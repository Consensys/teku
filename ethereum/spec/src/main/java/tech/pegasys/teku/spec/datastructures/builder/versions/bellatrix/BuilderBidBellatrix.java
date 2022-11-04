package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderBellatrix;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidBellatrix
    extends Container3<
        BuilderBidBellatrix, ExecutionPayloadHeaderBellatrix, SszUInt256, SszPublicKey>
    implements BuilderBid {

  protected BuilderBidBellatrix(BuilderBidSchemaBellatrix schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected BuilderBidBellatrix(
      BuilderBidSchemaBellatrix schema,
      ExecutionPayloadHeaderBellatrix executionPayloadHeader,
      SszUInt256 value,
      SszPublicKey publicKey) {
    super(schema, executionPayloadHeader, value, publicKey);
  }

  @Override
  public ExecutionPayloadHeaderBellatrix getExecutionPayloadHeader() {
    return getField0();
  }

  @Override
  public UInt256 getValue() {
    return getField1().get();
  }

  @Override
  public BLSPublicKey getPublicKey() {
    return getField2().getBLSPublicKey();
  }

  @Override
  public BuilderBidSchemaBellatrix getSchema() {
    return (BuilderBidSchemaBellatrix) super.getSchema();
  }
}
