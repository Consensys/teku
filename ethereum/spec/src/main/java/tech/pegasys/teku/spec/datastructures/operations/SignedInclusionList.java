package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedInclusionList extends Container2<SignedInclusionList, InclusionList, SszSignature> {

    public SignedInclusionList(
            final SignedInclusionListSchema type, final TreeNode backingNode) {
        super(type, backingNode);
    }

    public SignedInclusionList(
            final SignedInclusionListSchema schema,
            final InclusionList message,
            final BLSSignature signature) {
        super(schema, message, new SszSignature(signature));
    }

    @Override
    public SignedInclusionListSchema getSchema() {
        return (SignedInclusionListSchema) super.getSchema();
    }

    public InclusionList getMessage() {
        return getField0();
    }

    public BLSSignature getSignature() {
        return getField1().getSignature();
    }
}
