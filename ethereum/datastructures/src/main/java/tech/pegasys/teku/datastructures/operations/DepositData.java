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

package tech.pegasys.teku.datastructures.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class DepositData implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 2;

  private final BLSPublicKey pubkey;
  private final Bytes32 withdrawal_credentials;
  private final UInt64 amount;
  private BLSSignature signature; // Signing over DepositMessage

  public DepositData(
      BLSPublicKey pubkey, Bytes32 withdrawal_credentials, UInt64 amount, BLSSignature signature) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.amount = amount;
    this.signature = signature;
  }

  public DepositData(final DepositMessage depositMessage, final BLSSignature signature) {
    this(
        depositMessage.getPubkey(),
        depositMessage.getWithdrawal_credentials(),
        depositMessage.getAmount(),
        signature);
  }

  public DepositData() {
    this.pubkey = BLSPublicKey.empty();
    this.withdrawal_credentials = Bytes32.ZERO;
    this.amount = UInt64.ZERO;
    this.signature = BLSSignature.empty();
  }

  @Override
  public int getSSZFieldCount() {
    return pubkey.getSSZFieldCount() + SSZ_FIELD_COUNT + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(pubkey.get_fixed_parts());
    fixedPartsList.addAll(
        List.of(
            SSZ.encode(writer -> writer.writeFixedBytes(withdrawal_credentials)),
            SSZ.encodeUInt64(amount.longValue())));
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawal_credentials, amount, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DepositData)) {
      return false;
    }

    DepositData other = (DepositData) obj;
    return Objects.equals(this.getPubkey(), other.getPubkey())
        && Objects.equals(this.getWithdrawal_credentials(), other.getWithdrawal_credentials())
        && Objects.equals(this.getAmount(), other.getAmount())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BLSPublicKey getPubkey() {
    return pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public UInt64 getAmount() {
    return amount;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, pubkey.toSSZBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, withdrawal_credentials),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toSSZBytes())));
  }
}
