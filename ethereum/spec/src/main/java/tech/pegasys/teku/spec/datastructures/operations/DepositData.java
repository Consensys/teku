/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.types.SszPublicKey;
import tech.pegasys.teku.datastructures.types.SszPublicKeySchema;
import tech.pegasys.teku.datastructures.types.SszSignature;
import tech.pegasys.teku.datastructures.types.SszSignatureSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container4;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema4;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class DepositData
    extends Container4<DepositData, SszPublicKey, SszBytes32, SszUInt64, SszSignature> {

  public static class DepositDataSchema
      extends ContainerSchema4<DepositData, SszPublicKey, SszBytes32, SszUInt64, SszSignature> {

    public DepositDataSchema() {
      super(
          "DepositData",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    @Override
    public DepositData createFromBackingNode(TreeNode node) {
      return new DepositData(this, node);
    }
  }

  public static final DepositDataSchema SSZ_SCHEMA = new DepositDataSchema();

  private DepositData(DepositDataSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositData(
      BLSPublicKey pubkey, Bytes32 withdrawal_credentials, UInt64 amount, BLSSignature signature) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        new SszBytes32(withdrawal_credentials),
        new SszUInt64(amount),
        new SszSignature(signature));
  }

  public DepositData(final DepositMessage depositMessage, final BLSSignature signature) {
    this(
        depositMessage.getPubkey(),
        depositMessage.getWithdrawal_credentials(),
        depositMessage.getAmount(),
        signature);
  }

  public DepositData() {
    super(SSZ_SCHEMA);
  }

  public BLSPublicKey getPubkey() {
    return getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawal_credentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  public BLSSignature getSignature() {
    return getField3().getSignature();
  }
}
