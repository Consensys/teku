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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class DepositData {

  private BLSPublicKey pubkey;
  private Bytes32 withdrawal_credentials;
  private UnsignedLong amount;
  private BLSSignature signature;

  public DepositData(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong amount,
      BLSSignature signature) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.amount = amount;
    this.signature = signature;
  }

  public static DepositData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new DepositData(
                BLSPublicKey.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(pubkey.toBytes());
          writer.writeFixedBytes(32, withdrawal_credentials);
          writer.writeUInt64(amount.longValue());
          writer.writeBytes(signature.toBytes());
        });
  }

  // TODO: check if this is correct
  public Bytes serialize() {
    return Bytes.wrap(
        pubkey.getPublicKey().toBytesCompressed(),
        withdrawal_credentials,
        Bytes.ofUnsignedLong(amount.longValue()),
        signature.toBytes());
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

  public void setPubkey(BLSPublicKey pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public UnsignedLong getAmount() {
    return amount;
  }

  public void setAmount(UnsignedLong amount) {
    this.amount = amount;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Bytes32 signing_root(String truncation_param) {
    if (!truncation_param.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(proposal, \"signature\") is currently supported for type Proposal.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleize(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, pubkey.toBytes()),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, withdrawal_credentials),
                HashTreeUtil.hash_tree_root(
                    SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())))));
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, pubkey.toBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, withdrawal_credentials),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, signature.toBytes())));
  }
}
