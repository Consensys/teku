package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

import java.time.Duration;

public class ExecutionProofsAvailabilityChecker implements AvailabilityChecker<ExecutionProof> {
    private final ExecutionProofManager executionProofManager;
    private final SafeFuture<DataAndValidationResult<ExecutionProof>> validationResult =
            new SafeFuture<>();
    private final SignedBeaconBlock block;

    public ExecutionProofsAvailabilityChecker(final ExecutionProofManager executionProofManager, final SignedBeaconBlock block) {
        this.executionProofManager = executionProofManager;
        this.block=block;
    }


    @Override
    public boolean initiateDataAvailabilityCheck() {
        executionProofManager.validateBlockWithExecutionProofs(block).orTimeout(Duration.ofSeconds(10)).propagateTo(validationResult);
        return true;
    }

    @Override
    public SafeFuture<DataAndValidationResult<ExecutionProof>> getAvailabilityCheckResult() {
        return validationResult;
    }
}
