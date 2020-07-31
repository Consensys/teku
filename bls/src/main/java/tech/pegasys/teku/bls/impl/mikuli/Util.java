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

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;

class Util {

  public static final BIG P = new BIG(ROM.Modulus);
  public static final Scalar curveOrder = new Scalar(new BIG(ROM.CURVE_Order));
  public static final G1Point g1Generator = new G1Point(ECP.generator());
  public static final G2Point g2Generator = new G2Point(ECP2.generator());

  static final BIG pDiv2 =
      BIG.fromBytes(
          Bytes.fromHexString(
                  "0x0d0088f51cbff34d258dd3db21a5d66bb23ba5c279c2895fb39869507b587b120f55ffff58a9ffffdcff7fffffffd555")
              .toArray());

  /**
   * Calculate the a1 flag in the Eth2 BLS spec. This is used to disambiguate Y, given X.
   *
   * <p>True if (y_im * 2) // P, false otherwise.
   *
   * @param yIm the imaginary part of the Y coordinate of the point
   * @return the y flag
   */
  static boolean calculateYFlag(BIG yIm) {
    return BIG.comp(yIm, pDiv2) > 0;
  }
}
