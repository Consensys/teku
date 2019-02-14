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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;

public class AttestationDataAndCustodyBit {

  private AttestationData data;
  private boolean custody_bit;

  public AttestationDataAndCustodyBit(AttestationData data, boolean custody_bit) {
    this.data = data;
    this.custody_bit = custody_bit;
  }

  public static AttestationDataAndCustodyBit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttestationDataAndCustodyBit(
                AttestationData.fromBytes(reader.readBytes()), reader.readBoolean()));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(data.toBytes());
          writer.writeBoolean(custody_bit);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, custody_bit);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttestationDataAndCustodyBit)) {
      return false;
    }

    AttestationDataAndCustodyBit other = (AttestationDataAndCustodyBit) obj;
    return Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getCustody_bit(), other.getCustody_bit());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public boolean getCustody_bit() {
    return custody_bit;
  }

  public void setCustody_bit(boolean custody_bit) {
    this.custody_bit = custody_bit;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }
}
