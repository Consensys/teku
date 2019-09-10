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

package tech.pegasys.artemis.util.bls;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BLSPublicKey implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;
  public static final int BLS_PUBKEY_SIZE = 48;

  /**
   * Generates a compressed, serialised, random, valid public key
   *
   * @return PublicKey The public key, not null
   */
  public static BLSPublicKey random() {
    return new BLSPublicKey(PublicKey.random());
  }

  public static BLSPublicKey random(int seed) {
    return new BLSPublicKey(PublicKey.random(seed));
  }

  /**
   * Creates an empty public key (all zero bytes)
   *
   * @return the empty public key as per the Eth2 spec
   */
  public static BLSPublicKey empty() {
    return BLSPublicKey.fromBytes(Bytes.wrap(new byte[BLS_PUBKEY_SIZE]));
  }

  public static BLSPublicKey aggregate(List<BLSPublicKey> publicKeys) {
    List<PublicKey> publicKeyObjects =
        publicKeys.stream().map(x -> x.publicKey).collect(Collectors.toList());
    return new BLSPublicKey(PublicKey.aggregate(publicKeyObjects));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(SSZ.encode(writer -> writer.writeFixedBytes(publicKey.toBytesCompressed())));
  }

  public static BLSPublicKey fromBytes(Bytes bytes) {
    checkArgument(
        bytes.size() == BLS_PUBKEY_SIZE,
        "Expected " + BLS_PUBKEY_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader ->
            new BLSPublicKey(
                PublicKey.fromBytesCompressed(reader.readFixedBytes(BLS_PUBKEY_SIZE))));
  }

  public static BLSPublicKey fromBytesCompressed(Bytes bytes) {
    return new BLSPublicKey(PublicKey.fromBytesCompressed(bytes));
  }

  private final PublicKey publicKey;

  public BLSPublicKey(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  /**
   * Returns the SSZ serialisation of the <em>compressed</em> form of the signature
   *
   * @return the serialisation of the compressed form of the signature.
   */
  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(publicKey.toBytesCompressed());
        });
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  @Override
  public String toString() {
    return publicKey.toString();
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
    return Objects.equals(this.getPublicKey(), other.getPublicKey());
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey);
  }
}
