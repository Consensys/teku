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

package tech.pegasys.artemis.datastructures.blocks;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

public final class Eth1Data {

  private Bytes32 deposit_root;
  private Bytes32 block_hash;

  public Eth1Data(Bytes32 deposit_root, Bytes32 block_hash) {
    this.deposit_root = deposit_root;
    this.block_hash = block_hash;
  }

  public Eth1Data(Eth1Data eth1Data) {
    this.deposit_root = eth1Data.getDeposit_root();
    this.block_hash = eth1Data.getBlock_hash();
  }

  public static Eth1Data fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader -> new Eth1Data(Bytes32.wrap(reader.readBytes()), Bytes32.wrap(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(deposit_root);
          writer.writeBytes(block_hash);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(deposit_root, block_hash);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Eth1Data)) {
      return false;
    }

    Eth1Data other = (Eth1Data) obj;
    return Objects.equals(this.getDeposit_root(), other.getDeposit_root())
        && Objects.equals(this.getBlock_hash(), other.getBlock_hash());
  }

  /** @return the deposit_root */
  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  /** @param deposit_root the deposit_root to set */
  public void setDeposit_root(Bytes32 deposit_root) {
    this.deposit_root = deposit_root;
  }

  /** @return the block_hash */
  public Bytes32 getBlock_hash() {
    return block_hash;
  }

  /** @param block_hash the block_hash to set */
  public void setBlock_hash(Bytes32 block_hash) {
    this.block_hash = block_hash;
  }
}
