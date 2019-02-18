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

package tech.pegasys.artemis.util.mikuli;

import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;

/** This class represents a Signature on G2 */
public final class Signature {

  /**
   * Aggregates list of Signature pairs
   *
   * @param signatures The list of signatures to aggregate, not null
   * @throws IllegalArgumentException if parameter list is empty
   * @return Signature, not null
   */
  public static Signature aggregate(List<Signature> signatures) {
    if (signatures.isEmpty()) {
      throw new IllegalArgumentException("Parameter list is empty");
    }
    return signatures.stream().reduce(Signature::combine).get();
  }

  /**
   * Decode a signature from its serialized representation.
   *
   * @param bytes the bytes of the signature
   * @return the signature
   */
  public static Signature decode(Bytes bytes) {
    G2Point point = G2Point.fromBytes(bytes);
    return new Signature(point);
  }

  private final G2Point point;

  Signature(G2Point point) {
    this.point = point;
  }

  /**
   * Combines this signature with another signature, creating a new signature.
   *
   * @param signature the signature to combine with
   * @return a new signature as combination of both signatures.
   */
  public Signature combine(Signature signature) {
    return new Signature(point.add(signature.point));
  }

  /**
   * Signature serialization
   *
   * @return byte array representation of the signature, not null
   */
  public Bytes encode() {
    return point.toBytes();
  }

  @Override
  public String toString() {
    return "Signature [ecpPoint=" + point.toString() + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((point == null) ? 0 : point.hashCode());
    return result;
  }

  G2Point g2Point() {
    return point;
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Signature)) {
      return false;
    }
    Signature other = (Signature) obj;
    return point.equals(other.point);
  }
}
