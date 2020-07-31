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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class BLSSignature implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;
  public static final int SSZ_BLS_SIGNATURE_SIZE = BLSConstants.BLS_SIGNATURE_SIZE;

  /**
   * Create a random, but valid, signature.
   *
   * <p>Generally prefer the seeded version.
   *
   * @return a random signature
   */
  static BLSSignature random() {
    return random(new Random().nextInt());
  }

  /**
   * Creates a random, but valid, signature based on a seed.
   *
   * @param entropy to seed the key pair generation
   * @return the signature
   */
  public static BLSSignature random(int entropy) {
    BLSKeyPair keyPair = BLSKeyPair.random(entropy);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    return BLS.sign(keyPair.getSecretKey(), Bytes.wrap(message));
  }

  /**
   * Creates an empty signature (all zero bytes). Note that this is not a valid signature.
   *
   * @return the empty signature
   */
  public static BLSSignature empty() {
    return BLSSignature.fromBytesCompressed(Bytes.wrap(new byte[SSZ_BLS_SIGNATURE_SIZE]));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(toSSZBytes());
  }

  public static BLSSignature fromBytesCompressed(Bytes bytes) {
    checkArgument(
        bytes.size() == BLSConstants.BLS_SIGNATURE_SIZE,
        "Expected " + BLSConstants.BLS_SIGNATURE_SIZE + " bytes but received %s.",
        bytes.size());
    return new BLSSignature(bytes);
  }

  public static BLSSignature fromSSZBytes(Bytes bytes) {
    checkArgument(
        bytes.size() == SSZ_BLS_SIGNATURE_SIZE,
        "Expected " + SSZ_BLS_SIGNATURE_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes, reader -> new BLSSignature(reader.readFixedBytes(SSZ_BLS_SIGNATURE_SIZE)));
  }

  // Sometimes we are dealing with random, invalid signature points, e.g. when testing.
  // Let's only interpret the raw data into a point when necessary to do so.
  // And vice versa while aggregating we are dealing with points only so let's
  // convert point to raw data when necessary to do so.
  private final Supplier<Signature> signature;
  private final Supplier<Bytes> bytesCompressed;

  /**
   * Construct from a Mikuli Signature object.
   *
   * @param signature A Mikuli Signature
   */
  BLSSignature(Signature signature) {
    this(() -> signature, Suppliers.memoize(signature::toBytesCompressed));
  }

  BLSSignature(Bytes signatureBytes) {
    this(
        Suppliers.memoize(() -> BLS.getBlsImpl().signatureFromCompressed(signatureBytes)),
        () -> signatureBytes);
  }

  private BLSSignature(Supplier<Signature> signature, Supplier<Bytes> bytesCompressed) {
    this.signature = signature;
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
          writer.writeFixedBytes(bytesCompressed.get());
        });
  }

  public Bytes toBytesCompressed() {
    return bytesCompressed.get();
  }

  Signature getSignature() {
    return signature.get();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toString();
  }

  @Override
  public int hashCode() {
    return toBytesCompressed().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BLSSignature)) {
      return false;
    }
    BLSSignature other = (BLSSignature) obj;
    return Objects.equals(toBytesCompressed(), other.toBytesCompressed());
  }
}
