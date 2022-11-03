package tech.pegasys.teku.spec.datastructures.execution;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;

public interface ExecutionPayload extends ExecutionPayloadSummary, SszContainer {

  SszList<Transaction> getTransactions();

  default Optional<Withdrawal> getOptionalWithdrawals() {
    return Optional.empty();
  }

  TreeNode getUnblindedNode();

  default Optional<ExecutionPayloadBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }
}
