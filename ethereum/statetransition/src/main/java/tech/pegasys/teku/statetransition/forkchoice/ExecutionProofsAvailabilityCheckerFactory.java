package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

public class ExecutionProofsAvailabilityCheckerFactory implements AvailabilityCheckerFactory<ExecutionProof> {

    private final ExecutionProofManager executionProofManager;
    private AvailabilityChecker<?> delegate;

    public ExecutionProofsAvailabilityCheckerFactory(ExecutionProofManager executionProofManager) {
        this.executionProofManager = executionProofManager;

    }

    public void setDelegate(final AvailabilityChecker<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public AvailabilityChecker<ExecutionProof> createAvailabilityChecker(final SignedBeaconBlock block) {
        return new ExecutionProofsAvailabilityChecker(executionProofManager, block, delegate);
    }
}
