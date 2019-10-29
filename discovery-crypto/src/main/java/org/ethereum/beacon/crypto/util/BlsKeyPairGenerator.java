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

package org.ethereum.beacon.crypto.util;

import java.math.BigInteger;
import java.util.Random;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.beacon.crypto.BLS381.KeyPair;
import org.ethereum.beacon.crypto.BLS381.PrivateKey;
import org.ethereum.beacon.crypto.bls.bc.BCParameters;
import tech.pegasys.artemis.util.bytes.Bytes32;

/**
 * Given a seed generates a sequence of {@link KeyPair} instances.
 *
 * <p><strong>Note:</strong> the implementation is not secure as it just uses {@link Random}.
 */
public class BlsKeyPairGenerator {
  private static final BigInteger MIN = BigInteger.ONE;
  private static final BigInteger MAX = BCParameters.ORDER.subtract(BigInteger.ONE);

  private Random random;

  private BlsKeyPairGenerator(Random random) {
    this.random = random;
  }

  public static BlsKeyPairGenerator create(long seed) {
    return new BlsKeyPairGenerator(new Random(seed));
  }

  public static BlsKeyPairGenerator createWithoutSeed() {
    return new BlsKeyPairGenerator(new Random());
  }

  public KeyPair next() {
    BigInteger value = BigInteger.ZERO;
    while (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
      byte[] bytes = new byte[Bytes32.SIZE];
      random.nextBytes(bytes);
      value = BigIntegers.fromUnsignedByteArray(bytes);
    }
    PrivateKey privateKey = PrivateKey.create(value);
    return KeyPair.create(privateKey);
  }
}
