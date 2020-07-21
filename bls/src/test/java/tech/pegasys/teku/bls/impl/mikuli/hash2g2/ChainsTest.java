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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.expChain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.h2Chain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.mxChain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.FP2Immutable.ONE;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.bigFromHex;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.ROM;
import org.junit.jupiter.api.Test;

class ChainsTest {

  // The field modulus
  private static final BIG P = new BIG(ROM.Modulus);

  // The enormous exponent used in mapToCurve
  private static final BIG THREE = new BIG(3);
  private static final DBIGExtended EXPONENT =
      new DBIGExtended(BIG.mul(P.plus(THREE), P.minus(THREE))).fshr(4);

  // A test value - a point on the curve
  private static JacobianPoint a =
      new JacobianPoint(
          new FP2Immutable(
              "0x0c8977fab5175ac2f09e5f39e29d016f11c094ef10f237d2a5e23f482d0bfb4466688527cd31685bfe481725c31462cc",
              "0x0b305838069012861bb63501841c91bd5bc7e1359d44cd196681fb14c03e544c22205bced326d490eb886aaa3ed52918"),
          new FP2Immutable(
              "0x172cf997b3501882861c07e852fadbf5753eb8a3e1d2ce375e6aed07cf9c1b5ff1cbf1124c6e3b0cf4607c683eafd1a4",
              "0x0d9dacf241a753d55cff6d45b568b716a2ad68ba29d23f92dea6e7cf6ed54e96cdac4a2b95213f93439b946ebc63349c"),
          new FP2Immutable(
              "0x05594bb289f0ebfd8fa3f020c6e1eaf4c49b97d8ccaf3470a3a02da4b3e7104778105bd6c7e0caf97206c77a8b501d4d",
              "0x0625151f905fad40eb0e2b9b0a46d9afe531256c6d5e39897a27d94700f037a761a741d11275180bd18e620289e02a16"));

  // Raise this element to a DBIG exponent. Used for testing expChain
  private static FP2Immutable pow(FP2Immutable a, DBIG exponent) {
    FP2Immutable result = ONE;
    DBIGExtended exp = new DBIGExtended(exponent);
    FP2Immutable tmp = new FP2Immutable(a);
    while (!exp.iszilch()) {
      if (exp.isOdd()) {
        result = result.mul(tmp);
      }
      tmp = tmp.sqr();
      exp.shr(1);
    }
    return result;
  }

  // Multiply this point by a BIG multiplier. Used for testing mxChain
  private static JacobianPoint multiply(JacobianPoint a, BIG multiplier) {
    JacobianPoint result = new JacobianPoint();
    BIG mul = new BIG(multiplier);
    JacobianPoint tmp = a;
    while (!mul.iszilch()) {
      if (mul.parity() == 1) {
        result = result.add(tmp);
      }
      tmp = tmp.dbl();
      mul.shr(1);
    }
    return result;
  }

  /**
   * Multiply the point by the G2 group cofactor. This is borrowed from the G2Point class.
   *
   * <p>Since the group cofactor is extremely large, we do a long multiplication.
   *
   * @param point the point to be scaled
   * @return a scaled point
   */
  private static ECP2 scaleWithCofactor(ECP2 point) {

    // These are a representation of the G2 cofactor (a 512 bit number)
    String upperHex =
        "0x0000000000000000000000000000000005d543a95414e7f1091d50792876a202cd91de4547085abaa68a205b2e5a7ddf";
    String lowerHex =
        "0x00000000000000000000000000000000a628f1cb4d9e82ef21537e293a6691ae1616ec6e786f0c70cf1c38e31c7238e5";
    String shiftHex =
        "0x000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000";

    BIG upper = bigFromHex(upperHex);
    BIG lower = bigFromHex(lowerHex);
    BIG shift = bigFromHex(shiftHex);

    ECP2 sum = new ECP2(point);
    sum = sum.mul(upper);
    sum = sum.mul(shift);

    ECP2 tmp = new ECP2(point);
    tmp = tmp.mul(lower);

    sum.add(tmp);

    return sum;
  }

  @Test
  void expChainTest() {
    // An arbitrary element
    FP2Immutable a =
        new FP2Immutable(
            bigFromHex(
                "0x081d1f51370a9e6f59ed62fa605e891c40b20d98601fe7c3fa6a8efabcf0c1c3a0ff05963ab388a4b9ec4d35e97c0863"),
            bigFromHex(
                "0x01fbe48c2b138982f28317f684364327114adecadd94b599347bded08ef7b7ba22d814f1c64f1c77023ec9425383c184"));
    FP2Immutable expected = pow(a, EXPONENT);
    FP2Immutable actual = expChain(a);
    assertEquals(expected, actual);
  }

  @Test
  void mxChainTest() {
    assertEquals(
        multiply(
            a,
            bigFromHex(
                "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000d201000000010000")),
        mxChain(a));
  }

  @Test
  void h2ChainTest() {
    assertEquals(new JacobianPoint(scaleWithCofactor(a.toECP2())), h2Chain(a));
  }
}
