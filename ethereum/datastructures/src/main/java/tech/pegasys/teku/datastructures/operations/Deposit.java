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
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.config.Constants;

public class Deposit implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private final SSZVector<Bytes32> proof; // Vector bounded by DEPOSIT_CONTRACT_TREE_DEPTH + 1
  private final DepositData data;

  public Deposit(SSZVector<Bytes32> proof, DepositData data) {
    this.proof = proof;
    this.data = data;
  }

  public Deposit() {
    this.proof = SSZVector.createMutable(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, Bytes32.ZERO);
    this.data = new DepositData();
  }

  public Deposit(DepositData data) {
    this.proof = null;
    this.data = data;
  }

  @Override
  public int getSSZFieldCount() {
    return data.getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(SSZ.encode(writer -> writer.writeFixedBytesVector(proof.asList()))));
    fixedPartsList.addAll(data.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(proof, data);
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
        && Objects.equals(this.getData(), other.getData());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SSZVector<Bytes32> getProof() {
    return proof;
  }

  public DepositData getData() {
    return data;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, proof),
            data.hash_tree_root()));
  }
}
