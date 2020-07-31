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

package tech.pegasys.teku.bls.impl.mikuli;

import com.google.common.base.Suppliers;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.PublicKey;

/** This class represents a BLS12-381 public key. */
public final class MikuliPublicKey implements PublicKey {

  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  /**
   * Generates a random, valid public key
   *
   * @return PublicKey The public key, not null
   */
  public static MikuliPublicKey random(final SecureRandom srng) {
    return MikuliKeyPair.random(srng).getPublicKey();
  }

  /**
   * Generates a random, valid public key given entropy
   *
   * @return PublicKey The public key, not null
   */
  public static MikuliPublicKey random(int entropy) {
    return MikuliKeyPair.random(entropy).getPublicKey();
  }

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty
   *
   * @param keys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  public static MikuliPublicKey aggregate(List<MikuliPublicKey> keys) {
    return keys.isEmpty()
        ? new MikuliPublicKey(new G1Point())
        : keys.stream().reduce(MikuliPublicKey::combine).get();
  }

  /**
   * Create a PublicKey from byte array
   *
   * @param bytes the bytes to read the public key from
   * @return a valid public key
   */
  public static MikuliPublicKey fromBytesCompressed(byte[] bytes) {
    return MikuliPublicKey.fromBytesCompressed(Bytes.wrap(bytes));
  }

  /**
   * Create a PublicKey from bytes
   *
   * @param bytes the bytes to read the public key from
   * @return the public key
   */
  public static MikuliPublicKey fromBytesCompressed(Bytes bytes) {
    return new MikuliPublicKey(bytes);
  }

  static MikuliPublicKey fromPublicKey(PublicKey publicKey) {
    if (publicKey instanceof MikuliPublicKey) {
      return (MikuliPublicKey) publicKey;
    } else {
      return fromBytesCompressed(publicKey.toBytesCompressed());
    }
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
  public MikuliPublicKey(MikuliSecretKey secretKey) {
    this(Util.g1Generator.mul(secretKey.getScalarValue()));
  }

  public MikuliPublicKey(G1Point point) {
    this.rawData = Suppliers.memoize(point::toBytes);
    this.point = () -> point;
  }

  public MikuliPublicKey(Bytes rawData) {
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

  public MikuliPublicKey combine(MikuliPublicKey pk) {
    return new MikuliPublicKey(point.get().add(pk.point.get()));
  }

  /**
   * Public key serialization
   *
   * @return byte array representation of the public key
   */
  @Override
  public Bytes48 toBytesCompressed() {
    Bytes data = rawData.get();
    return Bytes48.wrap(data.size() == COMPRESSED_PK_SIZE ? data : point.get().toBytesCompressed());
  }

  public G1Point g1Point() {
    return point.get();
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {
    g1Point();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }

  @Override
  public int hashCode() {
    try {
      return point.get().hashCode();
    } catch (final IllegalArgumentException e) {
      // Invalid point so only equal if it has the same raw data, hence use that hashCode.
      return rawData.hashCode();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MikuliPublicKey)) {
      return false;
    }
    MikuliPublicKey other = (MikuliPublicKey) obj;
    if (rawData.get().size() == other.rawData.get().size()
        && rawData.get().equals(other.rawData.get())) {
      return true;
    }
    try {
      return point.get().equals(other.point.get());
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }
}
