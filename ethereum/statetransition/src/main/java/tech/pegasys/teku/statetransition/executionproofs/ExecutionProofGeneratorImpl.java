package tech.pegasys.teku.statetransition.executionproofs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ExecutionProofGeneratorImpl implements ExecutionProofGenerator {

    private final SchemaDefinitionsElectra schemaDefinitionsElectra;
    private static final Logger LOG = LogManager.getLogger();

    public ExecutionProofGeneratorImpl(SchemaDefinitionsElectra schemaDefinitionsElectra) {
        this.schemaDefinitionsElectra = schemaDefinitionsElectra;
    }

    public SafeFuture<ExecutionProof> generateExecutionProof(final SignedBlockContainer blockContainer, final int subnetId) {
        final ExecutionPayload executionPayload = getExecutionPayload(blockContainer);
        final Bytes32 blockRoot = blockContainer.getSignedBlock().getRoot();
        final Bytes32 blockHash = executionPayload.getBlockHash();
        final Bytes dummyWitness =
                Bytes.of(
                        ("dummy_witness_for_block_" + blockHash.toHexString())
                                .getBytes(Charset.defaultCharset()));

            final ExecutionProof executionProof =
                    createProof(blockRoot, executionPayload, dummyWitness, UInt64.valueOf(subnetId));
            LOG.trace("Generated proof for subnet {}", executionProof.getSubnetId());

        return SafeFuture.completedFuture(executionProof);
    }

    private ExecutionPayload getExecutionPayload(final SignedBlockContainer blockContainer) {
        final BeaconBlock beaconBlock = blockContainer.getSignedBlock().getBeaconBlock().orElseThrow(() -> new  IllegalStateException("No beacon block found when generating execution proof"));
        final Optional<ExecutionPayload> optionalExecutionPayload = beaconBlock.getBody().getOptionalExecutionPayload();
        if (optionalExecutionPayload.isEmpty()) {
            throw new IllegalStateException("No execution payload present when generating execution proof");
        }
        return optionalExecutionPayload.get();

    }


    public ExecutionProof createProof(
            final Bytes32 blockRoot,
            final ExecutionPayload executionPayload,
            final Bytes dummyWitness,
            final UInt64 subnetId) {
        final Bytes32 blockHash = executionPayload.getBlockHash();
        final UInt64 blockNumber = executionPayload.getBlockNumber();
        final String dummyProof = getProof(blockHash, blockNumber, subnetId, dummyWitness);

        final ExecutionProofSchema executionProofSchema = schemaDefinitionsElectra.getExecutionProofSchema();

        return executionProofSchema.create(
                blockRoot,
                executionPayload.getBlockHash(),
                subnetId,
                UInt64.ONE,
                Bytes.of(dummyProof.getBytes(Charset.defaultCharset())));
    }

    private String getProof(final Bytes32 blockHash, final UInt64 blockNumber, final UInt64 subnetId, final Bytes dummyWitness) {
        return "dummy_proof_subnet_"
                        + subnetId.intValue()
                        + "_block_"
                        + blockHash.toHexString()
                        + "_number_"
                        + blockNumber.intValue()
                        + "_witness_len_"
                        + dummyWitness.size();
    }
}
