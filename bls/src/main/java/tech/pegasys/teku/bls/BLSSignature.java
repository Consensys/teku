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
import static java.util.Objects.isNull;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.mikuli.Signature;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class BLSSignature implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;
  public static final int BLS_SIGNATURE_SIZE = 96;

  /**
   * Create a random, but valid, signature.
   *
   * <p>Generally prefer the seeded version.
   *
   * @return a random signature
   */
  static BLSSignature random() {
    return new BLSSignature(Signature.random(new Random().nextInt()));
  }

  /**
   * Creates a random, but valid, signature based on a seed.
   *
   * @param entropy to seed the key pair generation
   * @return the signature
   */
  public static BLSSignature random(int entropy) {
    return new BLSSignature(Signature.random(entropy));
  }

  /**
   * Creates an empty signature (all zero bytes).
   *
   * @return the empty signature as per the Eth2 spec
   */
  public static BLSSignature empty() {
    return BLSSignature.fromBytes(Bytes.wrap(new byte[BLS_SIGNATURE_SIZE]));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(SSZ.encode(writer -> writer.writeFixedBytes(signature.toBytesCompressed())));
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    checkArgument(
        bytes.size() == BLS_SIGNATURE_SIZE,
        "Expected " + BLS_SIGNATURE_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader ->
            new BLSSignature(
                Signature.fromBytesCompressed(reader.readFixedBytes(BLS_SIGNATURE_SIZE))));
  }

  private final Signature signature;

  /**
   * Copy constructor.
   *
   * @param signature A BLSSignature
   */
  public BLSSignature(BLSSignature signature) {
    this.signature = signature.getSignature();
  }

  /**
   * Construct from a Mikuli Signature object.
   *
   * @param signature A Mikuli Signature
   */
  BLSSignature(Signature signature) {
    this.signature = signature;
  }

  /**
   * Returns the SSZ serialization of the <em>compressed</em> form of the signature.
   *
   * @return the serialization of the compressed form of the signature.
   */
  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(signature.toBytesCompressed());
        });
  }

  public Signature getSignature() {
    return signature;
  }

  @Override
  public String toString() {
    return signature.toString();
  }

  @Override
  public int hashCode() {
    return signature.hashCode();
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
    return Objects.equals(this.signature, other.signature);
  }
}
