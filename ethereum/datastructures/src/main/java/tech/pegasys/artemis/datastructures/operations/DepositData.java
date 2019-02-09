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
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;

public class DepositData {

  private DepositInput deposit_input;
  private UnsignedLong value;
  private UnsignedLong timestamp;

  public DepositData(DepositInput deposit_input, UnsignedLong value, UnsignedLong timestamp) {
    this.deposit_input = deposit_input;
    this.value = value;
    this.timestamp = timestamp;
  }

  public static DepositData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new DepositData(
                DepositInput.fromBytes(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(deposit_input.toBytes());
          writer.writeUInt64(value.longValue());
          writer.writeUInt64(timestamp.longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(deposit_input, value, timestamp);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DepositData)) {
      return false;
    }

    DepositData other = (DepositData) obj;
    return Objects.equals(this.getDeposit_input(), other.getDeposit_input())
        && Objects.equals(this.getValue(), other.getValue())
        && Objects.equals(this.getTimestamp(), other.getTimestamp());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public DepositInput getDeposit_input() {
    return deposit_input;
  }

  public void setDeposit_input(DepositInput deposit_input) {
    this.deposit_input = deposit_input;
  }

  public UnsignedLong getValue() {
    return value;
  }

  public void setValue(UnsignedLong value) {
    this.value = value;
  }

  public UnsignedLong getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(UnsignedLong timestamp) {
    this.timestamp = timestamp;
  }
}
