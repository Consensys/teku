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
import static java.util.Objects.isNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class BLSPublicKey {

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
   * <p>Due to the flags, this is not actually a valid key, so we use null to flag that the public
   * key is empty.
   *
   * @return the empty public key as per the Eth2 spec
   */
  public static BLSPublicKey empty() {
    return new BLSPublicKey(null);
  }

  public static BLSPublicKey aggregate(List<BLSPublicKey> publicKeys) {
    List<PublicKey> publicKeyObjects =
        publicKeys.stream().map(x -> x.publicKey).collect(Collectors.toList());
    return new BLSPublicKey(PublicKey.aggregate(publicKeyObjects));
  }

  public static BLSPublicKey fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 52, "Expected 52 bytes but received %s.", bytes.size());
    if (SSZ.decodeBytes(bytes).isZero()) {
      return BLSPublicKey.empty();
    } else {
      return SSZ.decode(
          bytes, reader -> new BLSPublicKey(PublicKey.fromBytesCompressed(reader.readBytes())));
    }
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
    if (isNull(publicKey)) {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(Bytes.wrap(new byte[48]));
          });
    } else {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(publicKey.toBytesCompressed());
          });
    }
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public boolean isEmpty() {
    return isNull(publicKey);
  }

  @Override
  public String toString() {
    return isNull(publicKey) ? "Empty Public Key" : publicKey.toString();
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
