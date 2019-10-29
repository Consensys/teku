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

package org.ethereum.beacon.crypto.bls.milagro;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.milagro.amcl.BLS381.ROM;

/**
 * Various utility methods to work with Milagro implementation of a finite field <code>
 * F<sub>q</sub>.
 *
 * @see FP
 * @see BIG
 */
public class FPs {

  /** Field modulus. */
  private static final BIG Q = new BIG(ROM.Modulus);

  /**
   * Calculates a sign of the value in a finite field <code>F<sub>q</sub></code>.
   *
   * <p>A sign is calculated by next formula: {@code sign = (value * 2) / q}.
   *
   * @param value a value.
   * @return calculated sign.
   */
  public static int getSign(BIG value) {
    DBIG d = new DBIG(value);
    d.add(d);
    return d.div(Q).bit(0);
  }

  /**
   * Uses imaginary part of {@link FP2} to calculate a sign if it's greater than zero, otherwise
   * real part is used.
   *
   * <p>Based on {@link #getSign(BIG)}.
   *
   * @param value a value.
   * @return calculated sign.
   */
  public static int getSign(FP2 value) {
    if (BIG.comp(value.getB(), new BIG()) > 0) {
      return getSign(value.getB());
    } else {
      return getSign(value.getA());
    }
  }

  /**
   * Negates value in a field <code>F<sub>q</sub></code>.
   *
   * @param value a value.
   * @return negated value.
   */
  public static BIG neg(BIG value) {
    FP fp = new FP(value);
    fp.neg();
    return fp.redc();
  }
}
