package tech.pegasys.teku.spec.datastructures.execution;

import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionProof extends Container4<ExecutionProof, SszBytes32,  SszUInt64, SszUInt64, SszVector<SszByte>> {

    public static class ExecutionProofSchema extends ContainerSchema4<ExecutionProof, SszBytes32,  SszUInt64, SszUInt64, SszVector<SszByte>> {
        public ExecutionProofSchema() {
            super(
                "ExecutionProof",
                namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
                namedSchema("subnet_id", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema("version", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema("proof_data", SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 65536)) // Assuming max size of proof_data is 65536 bytes
            );
        }

        @Override
        public ExecutionProof createFromBackingNode(final TreeNode node) {
            return new ExecutionProof(this, node);
        }

    }

    public static final ExecutionProofSchema SSZ_SCHEMA = new ExecutionProofSchema();

    private ExecutionProof(final ExecutionProofSchema type, final TreeNode node) {
        super(type, node);
    }

    public ExecutionProof(
        final SszBytes32 blockHash,
        final SszUInt64 subnetId,
        final SszUInt64 version,
        final SszVector<SszByte> proofData) {
        super(SSZ_SCHEMA, blockHash, subnetId, version, proofData);
    }


    public SszBytes32 getBlockHash() {
        return getField0();
    }
    public SszUInt64 getSubnetId() {
        return getField1();
    }

    public SszUInt64 getVersion() {
        return getField2();
    }

    public SszVector<SszByte> getProofData() {
        return getField3();
    }
}
