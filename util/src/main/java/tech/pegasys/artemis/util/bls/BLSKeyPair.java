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
import tech.pegasys.artemis.util.mikuli.KeyPair;

public class BLSKeyPair {

  private final BLSPublicKey publicKey;
  private final BLSSecretKey secretKey;

  public static BLSKeyPair random() {
    return new BLSKeyPair(KeyPair.random());
  }

  public static BLSKeyPair random(int entropy) {
    return new BLSKeyPair(KeyPair.random(entropy));
  }

  public BLSKeyPair(BLSPublicKey publicKey, BLSSecretKey secretKey) {
    this.publicKey = publicKey;
    this.secretKey = secretKey;
  }

  public BLSKeyPair(KeyPair keyPair) {
    this(new BLSPublicKey(keyPair.publicKey()), new BLSSecretKey(keyPair.secretKey()));
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public BLSSecretKey getSecretKey() {
    return secretKey;
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
