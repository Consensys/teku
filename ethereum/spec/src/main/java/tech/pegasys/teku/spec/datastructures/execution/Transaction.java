/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.datastructures.execution;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.impl.SszUnionImpl;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.impl.SszUnionSchemaImpl;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class Transaction extends SszUnionImpl {
  public static final int OPAQUE_TRANSACTION_SELECTOR = 0;

  public static class TransactionSchema extends SszUnionSchemaImpl<Transaction> {

    public TransactionSchema() {
      super(List.of(SszByteListSchema.create(SpecConfig.MAX_BYTES_PER_OPAQUE_TRANSACTION)));
    }

    public Transaction createOpaque(SszByteList bytes) {
      return createFromValue(OPAQUE_TRANSACTION_SELECTOR, bytes);
    }

    @SuppressWarnings("unchecked")
    public SszByteListSchema<?> getOpaqueTransactionSchema() {
      return (SszByteListSchema<?>) getChildSchema(OPAQUE_TRANSACTION_SELECTOR);
    }

    @Override
    public Transaction createFromBackingNode(TreeNode node) {
      return new Transaction(this, node);
    }
  }

  public static final TransactionSchema SSZ_SCHEMA = new TransactionSchema();

  private Transaction(TransactionSchema schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszByteList getOpaqueTransaction() {
    checkState(getSelector() == OPAQUE_TRANSACTION_SELECTOR);
    return (SszByteList) getValue();
  }
}
