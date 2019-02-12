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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

public class Crosslink {

  private UnsignedLong epoch;
  private Bytes32 shard_block_root;

  public Crosslink(UnsignedLong epoch, Bytes32 shard_block_root) {
    this.epoch = epoch;
    this.shard_block_root = shard_block_root;
  }

  /**
   * Generate random Crosslink.
   *
   * @return A Crosslink containing a random epoch and block root.
   */
  public static Crosslink random() {
    return new Crosslink(randomUnsignedLong(), Bytes32.random());
  }

  public static Crosslink fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Crosslink(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(epoch.longValue());
          // TODO: check this is right
          writer.writeBytes(shard_block_root);
        });
  }



  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getShard_block_root() {
    return shard_block_root;
  }

  public void setShard_block_root(Bytes32 shard_block_root) {
    this.shard_block_root = shard_block_root;
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }
}
