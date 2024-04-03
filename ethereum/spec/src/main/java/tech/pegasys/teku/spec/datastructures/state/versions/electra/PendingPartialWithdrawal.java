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

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PendingPartialWithdrawal
    extends Container3<PendingPartialWithdrawal, SszUInt64, SszUInt64, SszUInt64> {

  public static class PendingPartialWithdrawalSchema
      extends ContainerSchema3<PendingPartialWithdrawal, SszUInt64, SszUInt64, SszUInt64> {
    public PendingPartialWithdrawalSchema() {
      super(
          "PendingPartialWithdrawal",
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    public PendingPartialWithdrawal create(
        final SszUInt64 index, final SszUInt64 amount, final SszUInt64 epoch) {
      return new PendingPartialWithdrawal(this, index, amount, epoch);
    }

    public SszUInt64 getIndexSchema() {
      return (SszUInt64) getFieldSchema0();
    }

    public SszUInt64 getAmountSchema() {
      return (SszUInt64) getFieldSchema1();
    }

    public SszUInt64 getEpochSchema() {
      return (SszUInt64) getFieldSchema2();
    }

    @Override
    public PendingPartialWithdrawal createFromBackingNode(TreeNode node) {
      return null;
    }
  }

  private PendingPartialWithdrawal(
      PendingPartialWithdrawal.PendingPartialWithdrawalSchema type,
      final SszUInt64 index,
      final SszUInt64 amount,
      final SszUInt64 epoch) {
    super(type, index, amount, epoch);
  }

  public int getIndex() {
    return ((SszUInt64) get(0)).get().intValue();
  }

  public UInt64 getAmount() {
    return ((SszUInt64) get(1)).get();
  }

  public UInt64 getEpoch() {
    return ((SszUInt64) get(2)).get();
  }
}
