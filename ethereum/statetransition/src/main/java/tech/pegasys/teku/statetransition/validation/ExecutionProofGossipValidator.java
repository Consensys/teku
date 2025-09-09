package tech.pegasys.teku.statetransition.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

import java.util.Map;
import java.util.Set;

import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

public class ExecutionProofGossipValidator {
    private static final Logger LOG = LogManager.getLogger();

    private final Spec spec;
    private final Set<ExecutionProof> receivedValidExecutionProofSet;
    private ZkChainConfiguration zkChainConfiguration;

    public static ExecutionProofGossipValidator create(
            final Spec spec, final ZkChainConfiguration zkChainConfiguration) {

        return new ExecutionProofGossipValidator(spec, zkChainConfiguration,
                LimitedSet.createSynchronized(MAX_EXECUTION_PROOF_SUBNETS.intValue()));
    }

    public ExecutionProofGossipValidator(
            final Spec spec, final ZkChainConfiguration zkChainConfiguration,
            final Set<ExecutionProof> receivedValidExecutionProofSet) {
        this.spec = spec;
        this.zkChainConfiguration = zkChainConfiguration;
        this.receivedValidExecutionProofSet = receivedValidExecutionProofSet;
    }

    public SafeFuture<InternalValidationResult> validate(final ExecutionProof executionProof, final UInt64 subnetId) {
        if(executionProof.getSubnetId().longValue() != subnetId.longValue()) {
            LOG.warn("ExecutionProof subnetId does not match the gossip subnetId");
            return SafeFuture.completedFuture(InternalValidationResult.reject("SubnetId mismatch"));
        }


        if (receivedValidExecutionProofSet.contains(executionProof)) {
            // Already seen and valid
            return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
        }

        // Validated the execution proof
        receivedValidExecutionProofSet.add(executionProof);
        return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
    }
}
