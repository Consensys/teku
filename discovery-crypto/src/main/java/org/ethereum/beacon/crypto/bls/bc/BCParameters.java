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

package org.ethereum.beacon.crypto.bls.bc;

import java.math.BigInteger;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

/**
 * Various parameters that belong to {@code BLS12-381} signature scheme written with usage of Bouncy
 * Castle models and types.
 */
public interface BCParameters {

  /** Modulus of base finite field <code>F<sub>q</sub></code>. */
  BigInteger Q =
      new BigInteger(
          "1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab",
          16);

  /**
   * An order of cyclic groups <code>G<sub>1</sub></code>, <code>G<sub>2</sub></code> and <code>
   * G<sub>T</sub></code>.
   */
  BigInteger ORDER =
      new BigInteger("73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000001", 16);

  /** Parameter {@code a} in Weierstrass equation that defines {@code BLS12} curves. */
  BigInteger A = BigInteger.ZERO;
  /** Parameter {@code b} in Weierstrass equation that defines {@code BLS12} curves. */
  BigInteger B = BigInteger.valueOf(4);

  /** Length of the modulus {@link #Q} in bytes. */
  int Q_BYTE_LENGTH = Q.bitLength() / 8 + 1;

  /** Parameters of <code>G<sub>1</sub></code> subgroup. */
  abstract class G1 {
    private G1() {}

    /** Subgroup's cofactor. */
    public static final BigInteger COFACTOR =
        new BigInteger("396c8c005555e1568c00aaab0000aaab", 16);

    /** A {@code BLS12} curve defined over <code>F<sub>q</sub></code> field. */
    public static final ECCurve CURVE;
    /** A generator point of <code>G<sub>1</sub></code> subgroup. */
    public static final ECPoint G;

    static {
      CURVE = new ECCurve.Fp(Q, A, B, ORDER, COFACTOR);
      G =
          CURVE.createPoint(
              new BigInteger(
                  "17f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb",
                  16),
              new BigInteger(
                  "8b3f481e3aaa0f1a09e30ed741d8ae4fcf5e095d5d00af600db18cb2c04b3edd03cc744a2888ae40caa232946c5e7e1",
                  16));
    }
  }

  /** Parameters of <code>G<sub>2</sub></code> subgroup. */
  abstract class G2 {
    private G2() {}

    /** Subgroup's cofactor. */
    public static final BigInteger COFACTOR =
        new BigInteger(
            "5d543a95414e7f1091d50792876a202cd91de4547085abaa68a205b2e5a7ddfa628f1cb4d9e82ef21537e293a6691ae1616ec6e786f0c70cf1c38e31c7238e5",
            16);
  }
}
