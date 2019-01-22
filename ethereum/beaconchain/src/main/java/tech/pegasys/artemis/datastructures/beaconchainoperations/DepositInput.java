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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public final class DepositInput {

  public static DepositInput fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new DepositInput(
                Bytes32.wrap(reader.readBytes()),
                reader
                    .readBytesList()
                    .stream()
                    .map(Bytes48::wrap)
                    .collect(Collectors.toList())
                    .toArray(new Bytes48[0]),
                Bytes48.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes())));
  }

  private final Bytes48 pubkey;
  private final Bytes32 withdrawal_credentials;
  private final Bytes32 randao_commitment;
  private final Bytes32 poc_commitment;
  private final Bytes48[] proof_of_possession;

  public DepositInput(
      Bytes32 poc_commitment,
      Bytes48[] proof_of_possession,
      Bytes48 pubkey,
      Bytes32 randao_commitment,
      Bytes32 withdrawal_credentials) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.randao_commitment = randao_commitment;
    this.poc_commitment = poc_commitment;
    this.proof_of_possession = proof_of_possession;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepositInput that = (DepositInput) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(withdrawal_credentials, that.withdrawal_credentials)
        && Objects.equals(randao_commitment, that.randao_commitment)
        && Objects.equals(poc_commitment, that.poc_commitment)
        && Arrays.equals(proof_of_possession, that.proof_of_possession);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(pubkey, withdrawal_credentials, randao_commitment, poc_commitment);
    result = 31 * result + Arrays.hashCode(proof_of_possession);
    return result;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(poc_commitment);
          writer.writeBytesList(proof_of_possession);
          writer.writeBytes(pubkey);
          writer.writeBytes(randao_commitment);
          writer.writeBytes(withdrawal_credentials);
        });
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes48 getPubkey() {
    return pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public Bytes32 getRandao_commitment() {
    return randao_commitment;
  }

  public Bytes48[] getProof_of_possession() {
    return proof_of_possession;
  }

  public Bytes32 getPoc_commitment() {
    return poc_commitment;
  }
}
