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

package tech.pegasys.artemis.bls.mikuli;

import com.google.common.base.Suppliers;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;

/** This class represents a BLS12-381 public key. */
public final class PublicKey {

  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  /**
   * Generates a random, valid public key
   *
   * @return PublicKey The public key, not null
   */
  public static PublicKey random(final SecureRandom srng) {
    return KeyPair.random(srng).publicKey();
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
  static PublicKey aggregate(List<PublicKey> keys) {
    return keys.isEmpty()
        ? new PublicKey(new G1Point())
        : keys.stream().reduce(PublicKey::combine).get();
  }

  /**
   * Create a PublicKey from byte array
   *
   * @param bytes the bytes to read the public key from
   * @return a valid public key
   */
  public static PublicKey fromBytesCompressed(byte[] bytes) {
    return PublicKey.fromBytesCompressed(Bytes.wrap(bytes));
  }

  /**
   * Create a PublicKey from bytes
   *
   * @param bytes the bytes to read the public key from
   * @return the public key
   */
  public static PublicKey fromBytesCompressed(Bytes bytes) {
    return new PublicKey(bytes);
  }

  // Sometimes we are dealing with random, invalid signature points, e.g. when testing.
  // Let's only interpret the raw data into a point when necessary to do so.
  // And vice versa while aggregating we are dealing with points only so let's
  // convert point to raw data when necessary to do so.
  private final Supplier<Bytes> rawData;
  private final Supplier<G1Point> point;

  /**
   * Construct from a SecretKey
   *
   * @param secretKey
   */
  public PublicKey(SecretKey secretKey) {
    this(KeyPair.g1Generator.mul(secretKey.getScalarValue()));
  }

  PublicKey(G1Point point) {
    this.rawData = Suppliers.memoize(point::toBytes);
    this.point = () -> point;
  }

  PublicKey(Bytes rawData) {
    this.rawData = () -> rawData;
    this.point = Suppliers.memoize(() -> parsePublicKeyBytes(rawData));
  }

  private G1Point parsePublicKeyBytes(Bytes publicKeyBytes) {
    if (publicKeyBytes.size() == COMPRESSED_PK_SIZE) {
      return G1Point.fromBytesCompressed(publicKeyBytes);
    } else if (publicKeyBytes.size() == UNCOMPRESSED_PK_LENGTH) {
      return G1Point.fromBytes(publicKeyBytes);
    }
    throw new RuntimeException(
        "Expected either "
            + COMPRESSED_PK_SIZE
            + " or "
            + UNCOMPRESSED_PK_LENGTH
            + " bytes for public key, but found "
            + publicKeyBytes.size());
  }

  PublicKey combine(PublicKey pk) {
    return new PublicKey(point.get().add(pk.point.get()));
  }

  /**
   * Public key serialization
   *
   * @return byte array representation of the public key
   */
  public Bytes toBytesCompressed() {
    Bytes data = rawData.get();
    return (data.size() == COMPRESSED_PK_SIZE) ? data : point.get().toBytesCompressed();
  }

  public G1Point g1Point() {
    return point.get();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }

  @Override
  public int hashCode() {
    return point.get().hashCode();
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
    if (rawData.get().size() == other.rawData.get().size()
        && rawData.get().equals(other.rawData.get())) {
      return true;
    }
    return point.get().equals(other.point.get());
  }
}
