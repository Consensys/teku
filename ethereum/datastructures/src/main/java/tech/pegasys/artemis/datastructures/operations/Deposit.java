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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

public class Deposit {

  private List<Bytes32> merkle_branch;
  private UnsignedLong merkle_tree_index;
  private DepositData deposit_data;

  public Deposit(
      List<Bytes32> merkle_branch, UnsignedLong merkle_tree_index, DepositData deposit_data) {
    this.merkle_branch = merkle_branch;
    this.merkle_tree_index = merkle_tree_index;
    this.deposit_data = deposit_data;
  }

  public static Deposit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Deposit(
                reader.readBytesList().stream().map(Bytes32::wrap).collect(Collectors.toList()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                DepositData.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytesList(merkle_branch);
          writer.writeUInt64(merkle_tree_index.longValue());
          writer.writeBytes(deposit_data.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(merkle_branch, merkle_tree_index, deposit_data);
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
    return Objects.equals(this.getMerkle_branch(), other.getMerkle_branch())
        && Objects.equals(this.getMerkle_tree_index(), other.getMerkle_tree_index())
        && Objects.equals(this.getDeposit_data(), other.getDeposit_data());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Bytes32> getMerkle_branch() {
    return merkle_branch;
  }

  public void setMerkle_branch(List<Bytes32> merkle_branch) {
    this.merkle_branch = merkle_branch;
  }

  public UnsignedLong getMerkle_tree_index() {
    return merkle_tree_index;
  }

  public void setMerkle_tree_index(UnsignedLong merkle_tree_index) {
    this.merkle_tree_index = merkle_tree_index;
  }

  public DepositData getDeposit_data() {
    return deposit_data;
  }

  public void setDeposit_data(DepositData deposit_data) {
    this.deposit_data = deposit_data;
  }
}
