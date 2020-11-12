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

package tech.pegasys.teku.bls.impl.mikuli;

import java.util.Objects;
import java.util.Random;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.RAND;
import tech.pegasys.teku.bls.impl.KeyPair;

/** KeyPair represents a public and private key. */
public final class MikuliKeyPair extends KeyPair {

  private static final BIG curveOrder = Util.curveOrder.value();

  /**
   * Generate a new random key pair
   *
   * @return a new random key pair
   */
  public static MikuliKeyPair random(final Random srng) {
    RAND rng = new RAND();
    byte[] b = new byte[128];
    srng.nextBytes(b);
    rng.seed(128, b);
    // secret key must be between 1 and (curveOrder - 1) inclusive
    BIG bigSecret = BIG.randomnum(curveOrder.minus(new BIG(1)), rng);
    bigSecret.plus(new BIG(1));

    MikuliSecretKey secretKey = new MikuliSecretKey(new Scalar(bigSecret));
    return new MikuliKeyPair(secretKey);
  }

  /**
   * Generate a new random key pair given entropy
   *
   * <p>Use this only for testing.
   *
   * @param entropy to seed the key pair generation
   * @return a new random key pair
   */
  public static MikuliKeyPair random(int entropy) {
    RAND rng = new RAND();
    rng.sirand(entropy);
    // secret key must be between 1 and (curveOrder - 1) inclusive
    BIG bigSecret = BIG.randomnum(curveOrder.minus(new BIG(1)), rng);
    bigSecret.plus(new BIG(1));

    MikuliSecretKey secretKey = new MikuliSecretKey(new Scalar(bigSecret));
    return new MikuliKeyPair(secretKey);
  }

  public MikuliKeyPair(MikuliSecretKey secretKey, MikuliPublicKey publicKey) {
    super(secretKey, publicKey);
  }

  public MikuliKeyPair(MikuliSecretKey secretKey) {
    this(secretKey, secretKey.derivePublicKey());
  }

  @Override
  public MikuliPublicKey getPublicKey() {
    return MikuliPublicKey.fromPublicKey(super.getPublicKey());
  }

  @Override
  public MikuliSecretKey getSecretKey() {
    return MikuliSecretKey.fromSecretKey(super.getSecretKey());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof KeyPair)) return false;
    final KeyPair keyPair = (KeyPair) o;
    return getSecretKey().equals(keyPair.getSecretKey())
        && getPublicKey().equals(keyPair.getPublicKey());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getSecretKey(), getPublicKey());
  }
}
