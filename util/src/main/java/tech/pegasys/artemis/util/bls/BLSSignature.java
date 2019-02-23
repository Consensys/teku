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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.Signature;

public final class BLSSignature {

  /**
   * Create a signature by signing the given message and domain with the given private key
   *
   * @param keyPair the public and private key
   * @param message ***This will change to the digest of the message***
   * @param domain the signature domain as per the Eth2 spec
   * @return the resulting signature
   */
  public static BLSSignature sign(BLSKeyPair keyPair, Bytes message, long domain) {
    return new BLSSignature(
        BLS12381
            .sign(new KeyPair(keyPair.secretKey(), keyPair.publicKey()), message, domain)
            .signature());
  }

  /**
   * Create a random, but valid, signature
   *
   * @return a random signature
   */
  public static BLSSignature random() {
    return new BLSSignature(Signature.random());
  }

  /**
   * Create an empty signature (all zero bytes) as defined in the Eth2 BLS spec
   *
   * <p>Due to the flags, this is not actually a valid signature, so we need some extra logic to
   * flag it as empty
   *
   * @return the empty signature as per the Eth2 spec
   */
  public static BLSSignature empty() {
    BLSSignature signature = new BLSSignature(Signature.decode(Bytes.of(new byte[192])));
    signature.isEmpty = true;
    return signature;
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    // TODO: this doesn't handle the empty signature. Do we ever need to SSZ deserialise this?
    return SSZ.decode(
        bytes, reader -> new BLSSignature(Signature.decodeCompressed(reader.readBytes())));
  }

  private final Signature signature;
  private boolean isEmpty = false;

  public BLSSignature(Signature signature) {
    this.signature = signature;
  }

  /**
   * Verify the signature against the given public key, message and domain
   *
   * @param publicKey the public key of the key pair that signed the message
   * @param message the message
   * @param domain the domain as specified in the Eth2 spec
   * @return true if the signature is valid, false if it is not
   */
  boolean checkSignature(Bytes48 publicKey, Bytes message, long domain) {
    return !isEmpty && BLS12381.verify(PublicKey.fromBytes(publicKey), signature, message, domain);
  }

  /**
   * Returns the SSZ serialisation of the <em>compressed</em> form of the signature
   *
   * @return the serialisation of the compressed form of the signature.
   */
  public Bytes toBytes() {
    if (isEmpty) {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(Bytes.wrap(new byte[96]));
          });
    } else {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(signature.encodeCompressed());
          });
    }
  }

  public Signature getSignature() {
    return signature;
  }

  boolean isEmpty() {
    return isEmpty;
  }

  @Override
  public String toString() {
    return signature.toString();
  }

  @Override
  public int hashCode() {
    // TODO incorporate isEmpty into hashcode
    return signature.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BLSSignature)) {
      return false;
    }
    BLSSignature other = (BLSSignature) obj;
    return signature.equals(other.signature);
  }
}
