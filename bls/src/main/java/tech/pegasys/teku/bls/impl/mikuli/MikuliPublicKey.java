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

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.DeserializeException;
import tech.pegasys.teku.bls.impl.PublicKey;

/** This class represents a BLS12-381 public key. */
public final class MikuliPublicKey implements PublicKey {

  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty
   *
   * @param keys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  public static MikuliPublicKey aggregate(List<? extends PublicKey> keys) {
    return keys.isEmpty()
        ? new MikuliPublicKey(new G1Point())
        : keys.stream().map(MikuliPublicKey::fromPublicKey).reduce(MikuliPublicKey::combine).get();
  }

  /**
   * Create a PublicKey from bytes
   *
   * @param bytes the bytes to read the public key from
   * @return the public key
   */
  public static MikuliPublicKey fromBytesCompressed(Bytes bytes) {
    return new MikuliPublicKey(parsePublicKeyBytes(bytes));
  }

  static MikuliPublicKey fromPublicKey(PublicKey publicKey) {
    if (publicKey instanceof MikuliPublicKey) {
      return (MikuliPublicKey) publicKey;
    } else {
      return fromBytesCompressed(publicKey.toBytesCompressed());
    }
  }

  private static G1Point parsePublicKeyBytes(Bytes publicKeyBytes) {
    if (publicKeyBytes.size() == COMPRESSED_PK_SIZE) {
      return G1Point.fromBytesCompressed(publicKeyBytes);
    } else if (publicKeyBytes.size() == UNCOMPRESSED_PK_LENGTH) {
      return G1Point.fromBytes(publicKeyBytes);
    }
    throw new DeserializeException(
        "Expected either "
            + COMPRESSED_PK_SIZE
            + " or "
            + UNCOMPRESSED_PK_LENGTH
            + " bytes for public key, but found "
            + publicKeyBytes.size());
  }

  private final G1Point point;

  public MikuliPublicKey(G1Point point) {
    this.point = point;
  }

  public MikuliPublicKey combine(MikuliPublicKey pk) {
    if (this.isInfinity()) return this;
    if (pk.isInfinity()) return pk;
    return new MikuliPublicKey(point.add(pk.point));
  }

  public boolean isInfinity() {
    return point.getPoint().is_infinity();
  }

  /**
   * Public key serialization
   *
   * @return byte array representation of the public key
   */
  @Override
  public Bytes48 toBytesCompressed() {
    return Bytes48.wrap(point.toBytesCompressed());
  }

  public G1Point g1Point() {
    return point;
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {}

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
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
    MikuliPublicKey other = MikuliPublicKey.fromPublicKey((PublicKey) obj);
    try {
      return point.equals(other.point);
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }
}
