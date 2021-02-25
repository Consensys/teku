/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class DepositMessage
    extends Container3<DepositMessage, SszVector<SszByte>, SszBytes32, SszUInt64> {

  public static class DepositMessageSchema
      extends ContainerSchema3<DepositMessage, SszVector<SszByte>, SszBytes32, SszUInt64> {

    public DepositMessageSchema() {
      super(
          "DepositMessage",
          namedSchema("pubkey", SszComplexSchemas.BYTES_48_SCHEMA),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public DepositMessage createFromBackingNode(TreeNode node) {
      return new DepositMessage(this, node);
    }
  }

  public static final DepositMessageSchema SSZ_SCHEMA = new DepositMessageSchema();

  private DepositMessage(DepositMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositMessage(
      final BLSPublicKey pubkey, final Bytes32 withdrawal_credentials, final UInt64 amount) {
    super(
        SSZ_SCHEMA,
        SszUtils.toSszByteVector(pubkey.toBytesCompressed()),
        new SszBytes32(withdrawal_credentials),
        new SszUInt64(amount));
  }

  public BLSPublicKey getPubkey() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(SszUtils.getAllBytes(getField0())));
  }

  public Bytes32 getWithdrawal_credentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }
}
