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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class DepositReceipt
    extends Container5<
        DepositReceipt, SszPublicKey, SszBytes32, SszUInt64, SszSignature, SszUInt64> {

  DepositReceipt(
      final DepositReceiptSchema schema,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final UInt64 index) {
    super(
        schema,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(amount),
        new SszSignature(signature),
        SszUInt64.of(index));
  }

  public static final DepositReceiptSchema SSZ_SCHEMA = new DepositReceiptSchema();

  DepositReceipt(final DepositReceiptSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BLSPublicKey getPubkey() {
    return getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  public BLSSignature getSignature() {
    return getField3().getSignature();
  }

  public UInt64 getIndex() {
    return getField4().get();
  }

  @Override
  public DepositReceiptSchema getSchema() {
    return (DepositReceiptSchema) super.getSchema();
  }
}
