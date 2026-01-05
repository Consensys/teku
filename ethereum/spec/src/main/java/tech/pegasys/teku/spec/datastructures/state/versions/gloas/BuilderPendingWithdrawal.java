/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderPendingWithdrawal
    extends Container4<BuilderPendingWithdrawal, SszByteVector, SszUInt64, SszUInt64, SszUInt64> {

  protected BuilderPendingWithdrawal(
      final BuilderPendingWithdrawalSchema schema,
      final Eth1Address feeRecipient,
      final UInt64 amount,
      final UInt64 builderIndex,
      final UInt64 withdrawableEpoch) {
    super(
        schema,
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszUInt64.of(amount),
        SszUInt64.of(builderIndex),
        SszUInt64.of(withdrawableEpoch));
  }

  protected BuilderPendingWithdrawal(
      final BuilderPendingWithdrawalSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Eth1Address getFeeRecipient() {
    return Eth1Address.fromBytes(getField0().getBytes());
  }

  public UInt64 getAmount() {
    return getField1().get();
  }

  public UInt64 getBuilderIndex() {
    return getField2().get();
  }

  public UInt64 getWithdrawableEpoch() {
    return getField3().get();
  }

  public BuilderPendingWithdrawal copyWithNewWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new BuilderPendingWithdrawal(
        getSchema(), getFeeRecipient(), getAmount(), getBuilderIndex(), withdrawableEpoch);
  }

  @Override
  public BuilderPendingWithdrawalSchema getSchema() {
    return (BuilderPendingWithdrawalSchema) super.getSchema();
  }
}
