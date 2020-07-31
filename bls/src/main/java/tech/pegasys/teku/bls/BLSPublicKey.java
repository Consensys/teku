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

package tech.pegasys.teku.bls;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public final class BLSPublicKey implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;
  public static final int SSZ_BLS_PUBKEY_SIZE = BLSConstants.BLS_PUBKEY_SIZE;

  /**
   * Generates a compressed, serialized, random, valid public key based on a seed.
   *
   * @return PublicKey The public key, not null
   */
  public static BLSPublicKey random(int seed) {
    return BLSKeyPair.random(seed).getPublicKey();
  }

  /**
   * Creates an empty public key (all zero bytes).
   *
   * @return the empty public key as per the Eth2 spec
   */
  public static BLSPublicKey empty() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.ZERO);
  }

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty
   *
   * @param publicKeys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  public static BLSPublicKey aggregate(List<BLSPublicKey> publicKeys) {
    return new BLSPublicKey(
        BLS.getBlsImpl()
            .aggregatePublicKeys(
                publicKeys.stream().map(BLSPublicKey::getPublicKey).collect(Collectors.toList())));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(toSSZBytes());
  }

  public static BLSPublicKey fromSSZBytes(Bytes bytes) {
    checkArgument(
        bytes.size() == SSZ_BLS_PUBKEY_SIZE,
        "Expected " + SSZ_BLS_PUBKEY_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader -> new BLSPublicKey(Bytes48.wrap(reader.readFixedBytes(SSZ_BLS_PUBKEY_SIZE))));
  }

  /**
   * Create a PublicKey from 48-byte compressed format
   *
   * @param bytes 48 bytes to read the public key from
   * @return a public key. Note that implementation may lazily evaluate passed bytes so the method
   *     may not immediately fail if the supplied bytes are invalid. Use {@link
   *     BLSPublicKey#fromBytesCompressedValidate(Bytes48)} to validate immediately
   * @throws IllegalArgumentException If the supplied bytes are not a valid public key However if
   *     implementing class lazily parses bytes the exception might not be thrown on invalid input
   *     but throw on later usage. Use {@link BLSPublicKey#fromBytesCompressedValidate(Bytes48)} if
   *     need to immediately ensure input validity
   */
  public static BLSPublicKey fromBytesCompressed(Bytes48 bytes) throws IllegalArgumentException {
    return new BLSPublicKey(bytes);
  }

  public static BLSPublicKey fromBytesCompressedValidate(Bytes48 bytes)
      throws IllegalArgumentException {
    BLSPublicKey ret = new BLSPublicKey(bytes);
    ret.getPublicKey().forceValidation();
    return ret;
  }

  // Sometimes we are dealing with random, invalid pubkey points, e.g. when testing.
  // Let's only interpret the raw data into a point when necessary to do so.
  // And vice versa while aggregating we are dealing with points only so let's
  // convert point to raw data when necessary to do so.
  private final Supplier<PublicKey> publicKey;
  private final Supplier<Bytes48> bytesCompressed;

  /**
   * Construct from a BLSSecretKey object.
   *
   * @param secretKey A BLSSecretKey
   */
  public BLSPublicKey(BLSSecretKey secretKey) {
    this(secretKey.getSecretKey().derivePublicKey());
  }

  /**
   * Construct from a Mikuli PublicKey object.
   *
   * @param publicKey A Mikuli PublicKey
   */
  BLSPublicKey(PublicKey publicKey) {
    this(() -> publicKey, Suppliers.memoize(publicKey::toBytesCompressed));
  }

  BLSPublicKey(Bytes48 bytesCompressed) {
    this(
        Suppliers.memoize(() -> BLS.getBlsImpl().publicKeyFromCompressed(bytesCompressed)),
        () -> bytesCompressed);
  }

  private BLSPublicKey(Supplier<PublicKey> publicKey, Supplier<Bytes48> bytesCompressed) {
    this.publicKey = publicKey;
    this.bytesCompressed = bytesCompressed;
  }

  /**
   * Returns the SSZ serialization of the <em>compressed</em> form of the signature.
   *
   * @return the serialization of the compressed form of the signature.
   */
  public Bytes toSSZBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(toBytesCompressed());
        });
  }

  public Bytes48 toBytesCompressed() {
    return bytesCompressed.get();
  }

  PublicKey getPublicKey() {
    return publicKey.get();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BLSPublicKey)) {
      return false;
    }

    BLSPublicKey other = (BLSPublicKey) obj;
    return Objects.equals(this.toBytesCompressed(), other.toBytesCompressed());
  }

  @Override
  public int hashCode() {
    return Objects.hash(toBytesCompressed());
  }
}
