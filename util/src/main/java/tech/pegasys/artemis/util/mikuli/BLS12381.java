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

package tech.pegasys.artemis.util.mikuli;

import static tech.pegasys.artemis.util.mikuli.G2Point.hashToG2;

import net.consensys.cava.bytes.Bytes;

/*
 * Adapted from the ConsenSys/mikuli (Apache 2 License) implementation:
 * https://github.com/ConsenSys/mikuli/blob/master/src/main/java/net/consensys/mikuli/crypto/*.java
 */

/**
 * This Boneh-Lynn-Shacham (BLS) signature implementation is constructed from a pairing friendly
 * elliptic curve, the BLS12-381 curve. It uses parameters as defined in
 * https://z.cash/blog/new-snark-curve and the points in groups G1 and G2 are defined
 * https://github.com/zkcrypto/pairing/blob/master/src/bls12_381/README.md
 *
 * <p>This class depends upon the Apache Milagro library being available. See
 * https://milagro.apache.org.
 *
 * <p>Apache Milagro can be included using the gradle dependency
 * 'org.miracl.milagro.amcl:milagro-crypto-java'.
 */
public final class BLS12381 {

  private BLS12381() {}

  /**
   * Generates a SignatureAndPublicKey.
   *
   * @param keyPair The public and private key pair, not null
   * @param message The message to sign, not null
   * @param domain The domain value added to the message
   * @return The SignatureAndPublicKey, not null
   */
  public static SignatureAndPublicKey sign(KeyPair keyPair, Bytes message, long domain) {
    G2Point hashInGroup2 = hashFunction(message, domain);
    G2Point sig = keyPair.secretKey().sign(hashInGroup2);
    return new SignatureAndPublicKey(new Signature(sig), keyPair.publicKey());
  }

  /**
   * Generates a SignatureAndPublicKey.
   *
   * @param keyPair The public and private key pair, not null
   * @param message The message to sign, not null
   * @param domain The domain value added to the message
   * @return The SignatureAndPublicKey, not null
   */
  public static SignatureAndPublicKey sign(KeyPair keyPair, byte[] message, long domain) {
    return sign(keyPair, Bytes.wrap(message), domain);
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * @param publicKey The public key, not null
   * @param signature The signature, not null
   * @param message The message data to verify, not null
   * @param domain The domain value added to the message
   * @return True if the verification is successful.
   */
  public static boolean verify(
      PublicKey publicKey, Signature signature, Bytes message, long domain) {
    G1Point g1Generator = KeyPair.g1Generator;

    if (!G2Point.isValid(signature.g2Point()) || !G1Point.isValid(publicKey.g1Point())) {
      return false;
    }

    G2Point hashInGroup2 = hashFunction(message, domain);
    GTPoint e1 = AtePairing.pair(publicKey.g1Point(), hashInGroup2);
    GTPoint e2 = AtePairing.pair(g1Generator, signature.g2Point());

    return e1.equals(e2);
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * @param publicKey The public key, not null
   * @param signature The signature, not null
   * @param message The message data to verify, not null
   * @param domain The domain value added to the message
   * @return True if the verification is successful.
   */
  public static boolean verify(
      PublicKey publicKey, Signature signature, byte[] message, long domain) {
    return verify(publicKey, signature, Bytes.wrap(message), domain);
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * @param sigAndPubKey The signature and public key, not null
   * @param message The message data to verify, not null
   * @param domain The domain value added to the message
   * @return True if the verification is successful, not null
   */
  public static boolean verify(SignatureAndPublicKey sigAndPubKey, byte[] message, long domain) {
    return verify(sigAndPubKey.publicKey(), sigAndPubKey.signature(), message, domain);
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * @param sigAndPubKey The public key, not null
   * @param message The message data to verify, not null
   * @param domain The domain value added to the message
   * @return True if the verification is successful.
   */
  public static boolean verify(SignatureAndPublicKey sigAndPubKey, Bytes message, long domain) {
    return verify(sigAndPubKey.publicKey(), sigAndPubKey.signature(), message, domain);
  }

  private static G2Point hashFunction(Bytes message, long domain) {
    return hashToG2(message, domain);
  }
}
