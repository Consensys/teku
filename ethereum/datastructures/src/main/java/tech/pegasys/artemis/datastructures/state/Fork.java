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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;

public class Fork {

  private long previous_version;
  private long current_version;
  private long epoch;

  public Fork(long previous_version, long current_version, long epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  public Fork(Fork fork) {
    this.previous_version = fork.getPrevious_version();
    this.current_version = fork.getCurrent_version();
    this.epoch = fork.getEpoch();
  }

  public static Fork fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes, reader -> new Fork(reader.readUInt64(), reader.readUInt64(), reader.readUInt64()));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(previous_version);
          writer.writeUInt64(current_version);
          writer.writeUInt64(epoch);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(previous_version, current_version, epoch);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Fork)) {
      return false;
    }

    Fork other = (Fork) obj;
    return Objects.equals(this.getPrevious_version(), other.getPrevious_version())
        && Objects.equals(this.getCurrent_version(), other.getCurrent_version())
        && Objects.equals(this.getEpoch(), other.getEpoch());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public long getPrevious_version() {
    return previous_version;
  }

  public void setPrevious_version(long previous_version) {
    this.previous_version = previous_version;
  }

  public long getCurrent_version() {
    return current_version;
  }

  public void setCurrent_version(long current_version) {
    this.current_version = current_version;
  }

  public long getEpoch() {
    return epoch;
  }

  public void setEpoch(long epoch) {
    this.epoch = epoch;
  }
}
