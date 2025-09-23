package tech.pegasys.teku.statetransition.executionproofs;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;

public interface ExecutionProofGenerator {
    SafeFuture<Void> generateExecutionProof(SignedBlockContainer blockContainer);
}
