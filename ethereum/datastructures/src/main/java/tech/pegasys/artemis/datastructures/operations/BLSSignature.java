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
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public final class BLSSignature {

  Bytes48 c0;
  Bytes48 c1;

  public BLSSignature(Bytes48 c0, Bytes48 c1) {
    this.c0 = c0;
    this.c1 = c1;
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BLSSignature(Bytes48.wrap(reader.readBytes()), Bytes48.wrap(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(c0);
          writer.writeBytes(c1);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(c0, c1);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BLSSignature)) {
      return false;
    }

    BLSSignature other = (BLSSignature) obj;
    return Objects.equals(this.getC0(), other.getC0())
        && Objects.equals(this.getC1(), other.getC1());
  }

  /** @return the c0 */
  public Bytes48 getC0() {
    return c0;
  }

  /** @param c0 the c0 to set */
  public void setC0(Bytes48 c0) {
    this.c0 = c0;
  }

  /** @return the c1 */
  public Bytes48 getC1() {
    return c1;
  }

  /** @param c1 the c1 to set */
  public void setC1(Bytes48 c1) {
    this.c1 = c1;
  }
}
