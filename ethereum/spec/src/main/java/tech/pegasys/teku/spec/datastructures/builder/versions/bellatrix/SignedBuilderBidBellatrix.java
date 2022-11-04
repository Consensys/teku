package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedBuilderBidBellatrix
    extends Container2<SignedBuilderBidBellatrix, BuilderBidBellatrix, SszSignature>
    implements SignedBuilderBid {

  SignedBuilderBidBellatrix(SignedBuilderBidSchemaBellatrix type, TreeNode backingNode) {
    super(type, backingNode);
  }

  SignedBuilderBidBellatrix(
      final SignedBuilderBidSchemaBellatrix type,
      final BuilderBidBellatrix message,
      final BLSSignature signature) {
    super(type, message, new SszSignature(signature));
  }

  @Override
  public SignedBuilderBidSchemaBellatrix getSchema() {
    return (SignedBuilderBidSchemaBellatrix) super.getSchema();
  }

  @Override
  public BuilderBid getMessage() {
    return getField0();
  }

  @Override
  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
