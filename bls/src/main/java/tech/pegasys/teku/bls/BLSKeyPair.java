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

import com.google.common.base.MoreObjects;
import java.security.SecureRandom;
import java.util.Objects;
import tech.pegasys.teku.bls.impl.KeyPair;

public final class BLSKeyPair {

  /**
   * Generate a key pair based on a randomly generated secret key.
   *
   * <p>This uses low-grade randomness and MUST NOT be used to generate production keys.
   *
   * @return a random key pair
   */
  public static BLSKeyPair random(final SecureRandom srng) {
    return new BLSKeyPair(BLS.getBlsImpl().generateKeyPair(srng));
  }

  /**
   * Generate a key pair based on a secret key generated from a seed value.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @return a keypair generated from a seed
   */
  public static BLSKeyPair random(int seed) {
    return new BLSKeyPair(BLS.getBlsImpl().generateKeyPair(seed));
  }

  private final BLSPublicKey publicKey;
  private final BLSSecretKey secretKey;

  /**
   * Construct from BLSPublicKey and BLSSecretKey
   *
   * @param publicKey a BLS public key
   * @param secretKey a BLS secret key
   */
  public BLSKeyPair(BLSPublicKey publicKey, BLSSecretKey secretKey) {
    this.publicKey = publicKey;
    this.secretKey = secretKey;
  }

  /**
   * Construct from a BLS secret key alone.
   *
   * @param secretKey a BLS secret key
   */
  public BLSKeyPair(BLSSecretKey secretKey) {
    this(new BLSPublicKey(secretKey), secretKey);
  }

  /**
   * Construct from a Mikuli key pair.
   *
   * @param keyPair a Mikuli key pair
   */
  private BLSKeyPair(KeyPair keyPair) {
    this(new BLSPublicKey(keyPair.getPublicKey()), new BLSSecretKey(keyPair.getSecretKey()));
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public BLSSecretKey getSecretKey() {
    return secretKey;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("secretKey", secretKey)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BLSKeyPair that = (BLSKeyPair) o;
    return publicKey.equals(that.publicKey) && secretKey.equals(that.secretKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, secretKey);
  }
}
