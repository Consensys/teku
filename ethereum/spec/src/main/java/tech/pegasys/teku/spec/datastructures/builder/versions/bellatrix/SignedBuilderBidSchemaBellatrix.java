package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBuilderBidSchemaBellatrix
    extends ContainerSchema2<SignedBuilderBidBellatrix, BuilderBidBellatrix, SszSignature>
    implements SignedBuilderBidSchema<SignedBuilderBidBellatrix> {

  public SignedBuilderBidSchemaBellatrix(final BuilderBidSchemaBellatrix builderBidSchema) {
    super(
        "SignedBuilderBidBellatrix",
        namedSchema("message", builderBidSchema),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedBuilderBidBellatrix create(
      final BuilderBidBellatrix message, final BLSSignature signature) {
    return new SignedBuilderBidBellatrix(this, message, signature);
  }

  @Override
  public SignedBuilderBidBellatrix createFromBackingNode(TreeNode node) {
    return new SignedBuilderBidBellatrix(this, node);
  }
}
