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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class WithdrawalRequest
    extends Container3<WithdrawalRequest, SszByteVector, SszPublicKey, SszUInt64> {

  public static final WithdrawalRequestSchema SSZ_SCHEMA = new WithdrawalRequestSchema();

  protected WithdrawalRequest(
      final WithdrawalRequestSchema schema,
      final Bytes20 sourceAddress,
      final BLSPublicKey validatorPublicKey,
      final UInt64 amount) {
    super(
        schema,
        SszByteVector.fromBytes(sourceAddress.getWrappedBytes()),
        new SszPublicKey(validatorPublicKey),
        SszUInt64.of(amount));
  }

  WithdrawalRequest(final WithdrawalRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Bytes20 getSourceAddress() {
    return new Bytes20(getField0().getBytes());
  }

  public BLSPublicKey getValidatorPublicKey() {
    return getField1().getBLSPublicKey();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  @Override
  public WithdrawalRequestSchema getSchema() {
    return (WithdrawalRequestSchema) super.getSchema();
  }
}
