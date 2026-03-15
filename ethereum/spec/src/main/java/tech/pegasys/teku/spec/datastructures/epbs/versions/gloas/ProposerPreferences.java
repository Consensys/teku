/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerPreferences
    extends Container4<ProposerPreferences, SszUInt64, SszUInt64, SszByteVector, SszUInt64> {

  protected ProposerPreferences(
      final ProposerPreferencesSchema schema,
      final UInt64 proposalSlot,
      final UInt64 validatorIndex,
      final Eth1Address feeRecipient,
      final UInt64 gasLimit) {
    super(
        schema,
        SszUInt64.of(proposalSlot),
        SszUInt64.of(validatorIndex),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszUInt64.of(gasLimit));
  }

  protected ProposerPreferences(
      final ProposerPreferencesSchema schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public UInt64 getProposalSlot() {
    return getField0().get();
  }

  public UInt64 getValidatorIndex() {
    return getField1().get();
  }

  public Eth1Address getFeeRecipient() {
    return Eth1Address.fromBytes(getField2().getBytes());
  }

  public UInt64 getGasLimit() {
    return getField3().get();
  }

  @Override
  public ProposerPreferencesSchema getSchema() {
    return (ProposerPreferencesSchema) super.getSchema();
  }
}
