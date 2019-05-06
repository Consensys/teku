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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;

public class Deposit implements Merkleizable {

  private List<Bytes32> proof; // Bounded by DEPOSIT_CONTRACT_TREE_DEPTH
  private UnsignedLong index;
  private DepositData deposit_data;

  public Deposit(List<Bytes32> proof, UnsignedLong index, DepositData deposit_data) {
    this.proof = proof;
    this.index = index;
    this.deposit_data = deposit_data;
  }

  public static Deposit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Deposit(
                reader.readFixedBytesList(Constants.DEPOSIT_CONTRACT_TREE_DEPTH, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                DepositData.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytesList(Constants.DEPOSIT_CONTRACT_TREE_DEPTH, 32, proof);
          writer.writeUInt64(index.longValue());
          writer.writeBytes(deposit_data.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(proof, index, deposit_data);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Deposit)) {
      return false;
    }

    Deposit other = (Deposit) obj;
    return Objects.equals(this.getProof(), other.getProof())
        && Objects.equals(this.getIndex(), other.getIndex())
        && Objects.equals(this.getDeposit_data(), other.getDeposit_data());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Bytes32> getProof() {
    return proof;
  }

  public void setProof(List<Bytes32> branch) {
    this.proof = branch;
  }

  public UnsignedLong getIndex() {
    return index;
  }

  public void setIndex(UnsignedLong index) {
    this.index = index;
  }

  public DepositData getDeposit_data() {
    return deposit_data;
  }

  public void setDeposit_data(DepositData deposit_data) {
    this.deposit_data = deposit_data;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // TODO Look at this - is this a TUPLE_OF_COMPOSITE
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, proof.toArray(new Bytes32[0])),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(index.longValue())),
            deposit_data.hash_tree_root()));
  }
}
