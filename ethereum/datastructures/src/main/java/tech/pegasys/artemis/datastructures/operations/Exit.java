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
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public class Exit {

  private UnsignedLong slot;
  private UnsignedLong validator_index;
  private List<Bytes48> signature;

  public Exit(UnsignedLong slot, UnsignedLong validator_index, List<Bytes48> signature) {
    this.slot = slot;
    this.validator_index = validator_index;
    this.signature = signature;
  }

  public static Exit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Exit(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                reader.readBytesList().stream().map(Bytes48::wrap).collect(Collectors.toList())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeUInt64(validator_index.longValue());
          writer.writeBytesList(signature);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, validator_index, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Exit)) {
      return false;
    }

    Exit other = (Exit) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getValidator_index(), other.getValidator_index())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(UnsignedLong validator_index) {
    this.validator_index = validator_index;
  }

  public List<Bytes48> getSignature() {
    return signature;
  }

  public void setSignature(List<Bytes48> signature) {
    this.signature = signature;
  }
}
