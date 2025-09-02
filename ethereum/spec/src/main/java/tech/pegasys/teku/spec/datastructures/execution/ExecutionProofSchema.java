package tech.pegasys.teku.spec.datastructures.execution;

import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionProofSchema
        extends ContainerSchema4<
        ExecutionProof, SszBytes32, SszUInt64, SszUInt64, SszVector<SszByte>> {

    public ExecutionProofSchema() {
        super(
                "ExecutionProof",
                namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
                namedSchema("subnet_id", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema("version", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema(
                        "proof_data",
                        SszVectorSchema.create(
                                SszPrimitiveSchemas.BYTE_SCHEMA,
                                65536)) // Assuming max size of proof_data is 65536 bytes
        );
    }

    @Override
    public ExecutionProof createFromBackingNode(final TreeNode node) {
        return new ExecutionProof(this, node);
    }
}