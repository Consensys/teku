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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public final class ValidatorRegistryDeltaBlock {

  public static ValidatorRegistryDeltaBlock fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new ValidatorRegistryDeltaBlock(
                UnsignedLong.fromLongBits(reader.readULong(64)),
                Bytes32.wrap(reader.readBytes()),
                Bytes48.wrap(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readULong(64)),
                reader.readInt(24)));
  }

  private final UnsignedLong flag;
  private final Bytes32 latest_registry_delta_root;
  private final Bytes48 pubkey;
  private final UnsignedLong slot;
  private final int validator_index;

  public ValidatorRegistryDeltaBlock(
      UnsignedLong flag,
      Bytes32 latest_registry_delta_root,
      Bytes48 pubkey,
      UnsignedLong slot,
      int validator_index) {
    this.flag = flag;
    this.latest_registry_delta_root = latest_registry_delta_root;
    this.pubkey = pubkey;
    this.slot = slot;
    this.validator_index = validator_index;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeULong(flag.longValue(), 64);
          writer.writeBytes(latest_registry_delta_root);
          writer.writeBytes(pubkey);
          writer.writeULong(slot.longValue(), 64);
          writer.writeInt(validator_index, 24);
        });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ValidatorRegistryDeltaBlock that = (ValidatorRegistryDeltaBlock) o;
    return validator_index == that.validator_index
        && Objects.equals(flag, that.flag)
        && Objects.equals(latest_registry_delta_root, that.latest_registry_delta_root)
        && Objects.equals(pubkey, that.pubkey)
        && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(flag, latest_registry_delta_root, pubkey, slot, validator_index);
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getLatest_registry_delta_root() {
    return latest_registry_delta_root;
  }

  public int getValidator_index() {
    return validator_index;
  }

  public Bytes48 getPubkey() {
    return pubkey;
  }

  public UnsignedLong getFlag() {
    return flag;
  }

  public UnsignedLong getSlot() {
    return slot;
  }
}
