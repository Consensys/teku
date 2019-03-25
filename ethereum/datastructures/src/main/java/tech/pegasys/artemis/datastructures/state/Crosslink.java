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
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;

public class Crosslink implements Copyable<Crosslink> {

  private UnsignedLong epoch;
  private Bytes32 crosslink_data_root;

  public Crosslink(UnsignedLong epoch, Bytes32 crosslink_data_root) {
    this.epoch = epoch;
    this.crosslink_data_root = crosslink_data_root;
  }

  public Crosslink(Crosslink crosslink) {
    this.epoch = crosslink.getEpoch();
    this.crosslink_data_root = crosslink.getCrosslink_data_root().copy();
  }

  public static Crosslink fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Crosslink(
                UnsignedLong.fromLongBits(reader.readUInt64()), Bytes32.wrap(reader.readBytes())));
  }

  @Override
  public Crosslink copy() {
    return new Crosslink(this);
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(epoch.longValue());
          writer.writeBytes(crosslink_data_root);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, crosslink_data_root);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Crosslink)) {
      return false;
    }

    Crosslink other = (Crosslink) obj;
    return Objects.equals(this.getEpoch(), other.getEpoch())
        && Objects.equals(this.getCrosslink_data_root(), other.getCrosslink_data_root());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getCrosslink_data_root() {
    return crosslink_data_root;
  }

  public void setCrosslink_data_root(Bytes32 shard_block_root) {
    this.crosslink_data_root = shard_block_root;
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }
}
