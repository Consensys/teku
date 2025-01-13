package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INCLUSION_LIST_SCHEMA;

public class SignedInclusionListSchema extends ContainerSchema2<SignedInclusionList, InclusionList, SszSignature> {

    public SignedInclusionListSchema(
            final String containerName, final SchemaRegistry schemaRegistry) {
        super(
                containerName,
                namedSchema("message", schemaRegistry.get(INCLUSION_LIST_SCHEMA)),
                namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public InclusionListSchema getInclusionListSchema() {
        return (InclusionListSchema) getFieldSchema0();
    }

    @Override
    public SignedInclusionList createFromBackingNode(final TreeNode node) {
        return new SignedInclusionList(this, node);
    }

    public SignedInclusionList create(
            final InclusionList message, final BLSSignature signature) {
        return new SignedInclusionList(this, message, signature);
    }

}
