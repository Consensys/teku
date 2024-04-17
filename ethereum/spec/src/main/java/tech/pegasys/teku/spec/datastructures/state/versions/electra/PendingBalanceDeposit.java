/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.versions.electra;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PendingBalanceDeposit extends Container2<PendingBalanceDeposit, SszUInt64, SszUInt64> {
  public static class PendingBalanceDepositSchema
      extends ContainerSchema2<PendingBalanceDeposit, SszUInt64, SszUInt64> {

    public PendingBalanceDepositSchema() {
      super(
          "PendingBalanceDeposit",
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public PendingBalanceDeposit createFromBackingNode(final TreeNode node) {
      return new PendingBalanceDeposit(this, node);
    }

    public PendingBalanceDeposit create(final SszUInt64 index, final SszUInt64 amount) {
      return new PendingBalanceDeposit(this, index, amount);
    }

    public SszUInt64 getIndexSchema() {
      return (SszUInt64) getFieldSchema0();
    }

    public SszUInt64 getAmountSchema() {
      return (SszUInt64) getFieldSchema1();
    }
  }

  private PendingBalanceDeposit(
      final PendingBalanceDepositSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private PendingBalanceDeposit(
      PendingBalanceDepositSchema type, final SszUInt64 index, final SszUInt64 amount) {
    super(type, index, amount);
  }

  public int getIndex() {
    return ((SszUInt64) get(0)).get().intValue();
  }

  public UInt64 getAmount() {
    return ((SszUInt64) get(1)).get();
  }
}
