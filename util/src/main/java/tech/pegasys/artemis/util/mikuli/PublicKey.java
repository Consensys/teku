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

/** This class represents a BLS12-381 public key. */
public final class PublicKey {

  /**
   * Generates a random, valid public key
   *
   * @return PublicKey The public key, not null
   */
  public static PublicKey random() {
    return KeyPair.random().publicKey();
  }

  /**
   * Generates a random, valid public key given entropy
   *
   * @return PublicKey The public key, not null
   */
  public static PublicKey random(int entropy) {
    return KeyPair.random(entropy).publicKey();
  }

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty
   *
   * @param keys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  public static PublicKey aggregate(List<PublicKey> keys) {
    if (keys.isEmpty()) {
      return new PublicKey(new G1Point());
    }
    return keys.stream().reduce((a, b) -> a.combine(b)).get();
  }

  /**
   * Create a PublicKey from byte array
   *
   * @param bytes the bytes to read the public key from
   * @return a valid public key
   */
  public static PublicKey fromBytesCompressed(byte[] bytes) {
    return fromBytesCompressed(Bytes.wrap(bytes));
  }

  /**
   * Create a PublicKey from bytes
   *
   * @param bytes the bytes to read the public key from
   * @return a valid public key
   */
  public static PublicKey fromBytesCompressed(Bytes bytes) {
    G1Point point = G1Point.fromBytesCompressed(bytes);
    return new PublicKey(point);
  }

  private final G1Point point;

  PublicKey(G1Point point) {
    this.point = point;
  }

  PublicKey(SecretKey secretKey) {
    this.point = KeyPair.g1Generator.mul(secretKey.getScalarValue());
  }

  PublicKey combine(PublicKey pk) {
    return new PublicKey(point.add(pk.point));
  }

  /**
   * Public key serialization
   *
   * @return byte array representation of the public key
   */
  public byte[] toByteArray() {
    return point.toBytes().toArrayUnsafe();
  }

  /**
   * Public key serialization
   *
   * @return byte array representation of the public key
   */
  public Bytes toBytesCompressed() {
    return point.toBytesCompressed();
  }

  G1Point g1Point() {
    return point;
  }

  @Override
  public String toString() {
    return point.toString();
  }

  @Override
  public int hashCode() {
    return point.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PublicKey)) {
      return false;
    }
    PublicKey other = (PublicKey) obj;
    return point.equals(other.point);
  }
}
