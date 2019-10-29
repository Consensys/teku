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
import org.apache.milagro.amcl.BLS381.ECP2;
import org.ethereum.beacon.crypto.bls.bc.BCParameters.G2;

/**
 * Various utility methods to work with Milagro implementation of {@code BLS12} elliptic curve
 * defined over a field <code>F<sub>q<sup>2</sup></sub></code>.
 *
 * @see ECP2
 */
public abstract class ECP2s {
  private ECP2s() {}

  private static final BIG COFACTOR_HIGH;
  private static final BIG COFACTOR_LOW;
  private static final BIG COFACTOR_SHIFT;

  static {
    byte[] bytes = G2.COFACTOR.toByteArray();
    assert bytes.length > BIG.MODBYTES;

    byte[] highPart = new byte[BIG.MODBYTES];
    byte[] lowPart = new byte[bytes.length - BIG.MODBYTES];

    System.arraycopy(bytes, 0, highPart, 0, BIG.MODBYTES);
    System.arraycopy(bytes, BIG.MODBYTES, lowPart, 0, bytes.length - BIG.MODBYTES);

    COFACTOR_HIGH = BIGs.fromByteArray(highPart, true);
    COFACTOR_LOW = BIGs.fromByteArray(lowPart, true);
    COFACTOR_SHIFT = new BIG(1);
    COFACTOR_SHIFT.shl(lowPart.length * 8);
  }

  /**
   * Multiplies point by a cofactor of <code>G<sub>2</sub></code> subgroup.
   *
   * <p>Milagro big number implementation handles numbers up to {@code 58} bytes length, it's
   * defined by a constant {@link BIG#BASEBITS}. Since <code>G<sub>2</sub></code> cofactor occupies
   * {@code 64} bytes there is no way to directly multiply point by the cofactor.
   *
   * <p>This workaround uses simple arithmetic trick: {@code res = p * COFACTOR_HIGH *
   * COFACTOR_SHIFT + p * COFACTOR_LOW}. Where {@code COFACTOR_HIGH} represents a high ordered word
   * of cofactor and {@code COFACTOR_LOW} is a low word. {@code COFACTOR_SHIFT} shifts high ordered
   * word.
   *
   * @param point a point.
   * @return a new point that is a result of multiplying given point by the cofactor.
   */
  public static ECP2 mulByG2Cofactor(ECP2 point) {
    ECP2 product = point.mul(COFACTOR_HIGH).mul(COFACTOR_SHIFT);
    product.add(point.mul(COFACTOR_LOW));
    return product;
  }
}
