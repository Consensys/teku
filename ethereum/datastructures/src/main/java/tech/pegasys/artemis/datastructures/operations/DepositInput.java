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

import java.util.Arrays;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public final class DepositInput {

  // BLS pubkey
  BLSPublicKey pubkey;
  // Withdrawal credentials
  Bytes32 withdrawal_credentials;
  // A BLS signature of this `DepositInput`
  BLSSignature proof_of_possession;

  public DepositInput(
      BLSPublicKey pubkey, Bytes32 withdrawal_credentials, BLSSignature proof_of_possession) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.proof_of_possession = proof_of_possession;
  }

  public static DepositInput fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new DepositInput(
                BLSPublicKey.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(pubkey.toBytes());
          writer.writeBytes(withdrawal_credentials);
          writer.writeBytes(proof_of_possession.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawal_credentials, proof_of_possession);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DepositInput)) {
      return false;
    }

    DepositInput other = (DepositInput) obj;
    return Objects.equals(this.getPubkey(), other.getPubkey())
        && Objects.equals(this.getWithdrawal_credentials(), other.getWithdrawal_credentials())
        && Objects.equals(this.getProof_of_possession(), other.getProof_of_possession());
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

  public BLSSignature getProof_of_possession() {
    return proof_of_possession;
  }

  public void setProof_of_possession(BLSSignature proof_of_possession) {
    this.proof_of_possession = proof_of_possession;
  }

  public Bytes32 signedRoot(String truncationParam) {
    if (!truncationParam.equals("proof_of_possession")) {
      throw new UnsupportedOperationException(
          "Only signed_root(proposal, \"proof_of_possession\") is currently supported for type Proposal.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleHash(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(pubkey.toBytes()),
                HashTreeUtil.hash_tree_root(withdrawal_credentials))));
  }
}
