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
            .sign(
                new KeyPair(
                    keyPair.getSecretKey().getSecretKey(), keyPair.getPublicKey().getPublicKey()),
                message,
                domain)
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
   * Creates a random, but valid, signature
   *
   * @param entropy to seed the key pair generation
   * @return the signature
   */
  public static BLSSignature random(int entropy) {
    return new BLSSignature(Signature.random(entropy));
  }

  /**
   * Creates an empty signature (all zero bytes)
   *
   * <p>Due to the flags, this is not actually a valid signature, so we use null to flag that the
   * signature is empty.
   *
   * @return the empty signature as per the Eth2 spec
   */
  public static BLSSignature empty() {
    return new BLSSignature(null);
  }

  /**
   * Aggregates a list of signatures into a single signature using BLS magic.
   *
   * @param signatures the list of signatures to be aggregated
   * @return the aggregated signature
   * @throws BLSException if an empty signature is found in the list to be aggregated
   */
  static BLSSignature aggregate(List<BLSSignature> signatures) throws BLSException {
    if (signatures.stream().anyMatch(BLSSignature::isEmpty)) {
      throw new BLSException("Empty signature found while aggregating signatures.");
    }
    List<Signature> signatureObjects =
        signatures.stream().map(x -> x.signature).collect(Collectors.toList());
    return new BLSSignature(Signature.aggregate(signatureObjects));
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 100, "Expected 100 bytes but received %s.", bytes.size());
    if (SSZ.decodeBytes(bytes).isZero()) {
      return BLSSignature.empty();
    } else {
      return SSZ.decode(
          bytes, reader -> new BLSSignature(Signature.fromBytesCompressed(reader.readBytes())));
    }
  }

  private final Signature signature;

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
   * @throws BLSException
   */
  boolean checkSignature(BLSPublicKey publicKey, Bytes message, long domain) throws BLSException {
    if (isNull(signature)) {
      throw new BLSException("The checkSignature method was called on an empty signature.");
    }
    if (isNull(publicKey)) {
      throw new BLSException("The checkSignature method was called with an empty public key.");
    }
    return BLS12381.verify(publicKey.getPublicKey(), signature, message, domain);
  }

  /**
   * Verify the aggregate signature against the given list of public keys, list of messages and
   * domain
   *
   * @param publicKeys the list of public keys signed the messages
   * @param messages the messages to be authenticated
   * @param domain the domain as specified in the Eth2 spec
   * @return true if the signature is valid, false if it is not
   * @throws BLSException
   */
  boolean checkSignature(List<BLSPublicKey> publicKeys, List<Bytes> messages, long domain)
      throws BLSException {
    checkArgument(
        publicKeys.size() == messages.size(),
        "Differing numbers of public keys and messages: %s and %s",
        publicKeys.size(),
        messages.size());
    if (isNull(signature)) {
      throw new BLSException("The checkSignature method was called on an empty signature.");
    }
    List<PublicKey> publicKeyObjects =
        publicKeys.stream().map(x -> x.getPublicKey()).collect(Collectors.toList());
    return BLS12381.verifyMultiple(publicKeyObjects, signature, messages, domain);
  }

  /**
   * Returns the SSZ serialisation of the <em>compressed</em> form of the signature
   *
   * @return the serialisation of the compressed form of the signature.
   */
  public Bytes toBytes() {
    if (isNull(signature)) {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(Bytes.wrap(new byte[96]));
          });
    } else {
      return SSZ.encode(
          writer -> {
            writer.writeBytes(signature.toBytesCompressed());
          });
    }
  }

  public Signature getSignature() {
    return signature;
  }

  boolean isEmpty() {
    return isNull(signature);
  }

  @Override
  public String toString() {
    return isNull(signature) ? "Empty Signature" : signature.toString();
  }

  @Override
  public int hashCode() {
    return isEmpty() ? 0 : signature.hashCode();
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
