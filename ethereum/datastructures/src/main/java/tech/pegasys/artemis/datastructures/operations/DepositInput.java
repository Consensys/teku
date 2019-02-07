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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public final class DepositInput {

  //TODO SSZ broken in DepositInput by v0.01
//  public static DepositInput fromBytes(Bytes bytes) {
//    return SSZ.decode(
//        bytes,
//        reader ->
//            new DepositInput(
//                Bytes32.wrap(reader.readBytes()),
//                reader.readBytesList().stream().map(Bytes48::wrap).collect(Collectors.toList()),
//                Bytes48.wrap(reader.readBytes()),
//                Bytes32.wrap(reader.readBytes()),
//                Bytes32.wrap(reader.readBytes())));
//  }
  //BLS pubkey
  Bytes48 pubkey;
  //Withdrawal credentials
  Bytes32 withdrawal_credentials;
  //A BLS signature of this `DepositInput`
  List<Bytes48> proof_of_possession;

  public DepositInput(Bytes48 pubkey, Bytes32 withdrawal_credentials, List<Bytes48> proof_of_possession) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.proof_of_possession = proof_of_possession;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes48 getPubkey() {
    return pubkey;
  }

  public void setPubkey(Bytes48 pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
  }

  public List<Bytes48> getProof_of_possession() {
    return proof_of_possession;
  }

  public void setProof_of_possession(ArrayList<Bytes48> proof_of_possession) {
    this.proof_of_possession = proof_of_possession;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepositInput that = (DepositInput) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(withdrawal_credentials, that.withdrawal_credentials)
        && Objects.equals(proof_of_possession, that.proof_of_possession);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(pubkey, withdrawal_credentials);
    result = 31 * result + proof_of_possession.hashCode();
    return result;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytesList(proof_of_possession.toArray(new Bytes48[0]));
          writer.writeBytes(pubkey);
          writer.writeBytes(withdrawal_credentials);
        });
  }


}
