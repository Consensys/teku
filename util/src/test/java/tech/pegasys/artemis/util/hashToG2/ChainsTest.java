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

package tech.pegasys.artemis.util.hashToG2;

import static org.junit.jupiter.api.Assertions.*;
import static tech.pegasys.artemis.util.hashToG2.Chains.expChain;
import static tech.pegasys.artemis.util.hashToG2.Chains.mxChain;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ONE;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.ROM;
import org.junit.jupiter.api.Test;

class ChainsTest {

  // The field modulus
  private static final BIG P = new BIG(ROM.Modulus);

  // The enormous exponent used in mapToCurve
  private static final BIG THREE = new BIG(3);
  private static final DBIGExtended EXPONENT =
      new DBIGExtended(BIG.mul(P.plus(THREE), P.minus(THREE))).fshr(4);

  /* Raise this element to a DBIG exponent. Used for testing expChain */
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

  @Test
  void mxChainTest() {
    JacobianPoint a =
        new JacobianPoint(
            new FP2Immutable(new BIG(1), new BIG(2)),
            new FP2Immutable(new BIG(3), new BIG(4)),
            new FP2Immutable(new BIG(5), new BIG(6)));
    JacobianPoint expected =
        new JacobianPoint(
            new FP2Immutable(
                "0x14eb89798ed67c7c0c9a7ab4627f64a9295fa5aa738b23bc41d7e57bc75e3cfec576007ea509a867a1746954aae2cca9",
                "0x00d29230f0dd01305ad70a52ceed8ee8e88ee072b69d9a535065785228d324b339a8b3116461ecfaec453c303da68240"),
            new FP2Immutable(
                "0x0170b865e217be3a5ecd56a0470270453cbd51ad2c04e0ff053455380a5a6841e5b580ff5dddbdf664ddc25acfecba58",
                "0x0020c819b3da9c3e6e856a5235a3bf28b2f1401340d3bd41deaad48d17cb1e100655dd7028f6cb1708dc239175f4205a"),
            new FP2Immutable(
                "0x00e34380275c83c4308fc707542a3ecefa0ca80aeffd3791bef2fc8fbfbbb970f41c34ed98454b5884f90a838eccb68a",
                "0x05b84312465a31b1dbe87388923b6244befe2f355ebda12b88f133237cf2c13158f1253b9e2f09749beb4099338957a4"));
    assertEquals(expected, mxChain(a));
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
  void h2ChainTest() {
    // TODO
  }


}
