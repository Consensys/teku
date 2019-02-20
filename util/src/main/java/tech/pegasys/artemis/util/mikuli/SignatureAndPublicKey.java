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

import java.util.List;

/** This class represents a signature and a public key */
public final class SignatureAndPublicKey {

  /**
   * Aggregates list of Signature and PublicKey pairs
   *
   * @param sigAndPubKeys The list of Signatures and corresponding Public keys to aggregate, not
   *     null
   * @return SignatureAndPublicKey, not null
   * @throws IllegalArgumentException if parameter list is empty
   */
  public static SignatureAndPublicKey aggregate(List<SignatureAndPublicKey> sigAndPubKeys) {
    if (sigAndPubKeys.isEmpty()) {
      throw new IllegalArgumentException("Parameter list is empty");
    }
    return sigAndPubKeys.stream().reduce((a, b) -> a.combine(b)).get();
  }

  private final Signature signature;
  private final PublicKey publicKey;

  SignatureAndPublicKey(Signature signature, PublicKey pubKey) {
    this.signature = signature;
    this.publicKey = pubKey;
  }

  /** @return the public key of the pair */
  public PublicKey publicKey() {
    return publicKey;
  }

  /** @return the signature of the pair */
  public Signature signature() {
    return signature;
  }

  /**
   * Combine the signature and public key provided to form a new signature and public key pair
   *
   * @param sigAndPubKey the signature and public key pair
   * @return a new signature and public key pair as a combination of both elements.
   */
  public SignatureAndPublicKey combine(SignatureAndPublicKey sigAndPubKey) {
    Signature newSignature = signature.combine(sigAndPubKey.signature);
    PublicKey newPubKey = publicKey.combine(sigAndPubKey.publicKey);
    return new SignatureAndPublicKey(newSignature, newPubKey);
  }
}
