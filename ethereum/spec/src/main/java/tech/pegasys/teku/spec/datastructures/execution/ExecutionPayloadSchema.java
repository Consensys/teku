package tech.pegasys.teku.spec.datastructures.execution;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.TRANSACTIONS;

import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public interface ExecutionPayloadSchema<T extends ExecutionPayload> extends SszContainerSchema<T> {

  @Override
  T createFromBackingNode(TreeNode node);

  TransactionSchema getTransactionSchema();

  SszListSchema<Transaction, ?> getTransactionsSchema();

  SszByteListSchema<?> getExtraDataSchema();

  default long getBlindedNodeGeneralizedIndex() {
    return getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS));
  }
}
