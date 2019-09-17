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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ONE;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ZERO;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.pow;
import static tech.pegasys.artemis.util.hashToG2.Util.P;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class FP2ImmutableTest {

  private static final FP2Immutable X = new FP2Immutable(new BIG(1), new BIG(1));
  private static final FP2Immutable XSQ = new FP2Immutable(new BIG(0), new BIG(2));
  // An eighth root of unity
  private static final BIG RV1 =
      BIG.fromBytes(
          Bytes.fromHexString(
                  "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09")
              .toArray());
  private static final FP2Immutable ROOT = new FP2Immutable(RV1, P.minus(RV1));

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalElements() {
    final FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    final FP2Immutable b = new FP2Immutable(new BIG(42), new BIG(69));
    assertEquals(a, b);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentElements() {
    final FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    final FP2Immutable b = new FP2Immutable(new BIG(69), new BIG(42));
    assertNotEquals(a, b);
  }

  @Test
  void fromHexadecimal() {
    final BIG P = new BIG(ROM.Modulus);
    final String hexPminus1 =
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaaa";
    final String hexPminus2 =
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaa9";
    final FP2Immutable expected = new FP2Immutable(P.minus(new BIG(1)), P.minus(new BIG(2)));
    final FP2Immutable actual = new FP2Immutable(hexPminus1, hexPminus2);
    assertEquals(expected, actual);
  }

  @Test
  void sqrTest() {
    assertEquals(XSQ, X.sqr());
  }

  @Test
  void mulTest() {
    assertEquals(XSQ, X.mul(X));
  }

  @Test
  void addTest() {
    FP2Immutable a = new FP2Immutable(new BIG(1), new BIG(2));
    FP2Immutable b = new FP2Immutable(new BIG(3), new BIG(4));
    FP2Immutable expected = new FP2Immutable(new BIG(4), new BIG(6));
    assertEquals(expected, a.add(b));
  }

  @Test
  void dblTest() {
    FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    FP2Immutable expected = new FP2Immutable(new BIG(84), new BIG(138));
    assertEquals(expected, a.dbl());
  }

  @Test
  void subTest() {
    FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    FP2Immutable b = new FP2Immutable(new BIG(1), new BIG(2));
    FP2Immutable expected = new FP2Immutable(new BIG(41), new BIG(67));
    assertEquals(expected, a.sub(b));
  }

  @Test
  void negTest() {
    FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    assertEquals(ZERO, a.add(a.neg()));
  }

  @Test
  void inverseTest() {
    FP2Immutable a = new FP2Immutable(new BIG(42), new BIG(69));
    assertEquals(ONE, a.mul(a.inverse()));
  }

  @Test
  void reduceTest() {
    FP2Immutable expected = new FP2Immutable(new BIG(42), new BIG(69));
    FP2Immutable a = new FP2Immutable(P.plus(new BIG(42)), P.plus(new BIG(69)));
    assertEquals(expected, a.reduce());
  }

  @Test
  void isZilchTest() {
    assertTrue(ZERO.iszilch());
    assertFalse(ONE.iszilch());
  }

  // integer exponent
  @Test
  void powIntTest() {
    assertEquals(ONE, pow(ROOT, 0));
    assertEquals(ROOT, pow(ROOT, 1));
    assertEquals(ONE, pow(ROOT, 8));
    assertEquals(ROOT, pow(ROOT, 9));
    assertEquals(ONE, pow(ROOT, 9872));
    assertEquals(ROOT, pow(ROOT, 9873));
  }

  // DBIG exponent
  @Test
  void powDbigTest() {
    assertEquals(ONE, pow(ROOT, new DBIG(0)));
    assertEquals(ROOT, pow(ROOT, new DBIG(1)));
    assertEquals(ONE, pow(ROOT, new DBIG(8)));
    assertEquals(ROOT, pow(ROOT, new DBIG(9)));
    assertEquals(ONE, pow(ROOT, new DBIG(9872)));
    assertEquals(ROOT, pow(ROOT, new DBIG(9873)));
  }

  // TODO: more rigorous tests (swapping getA and getB in the routine didn't change the result!)
  @Test
  void signEquals() {
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    FP2Immutable x = new FP2Immutable(thresh, thresh);
    assertEquals(1, x.sgn0());
  }

  @Test
  void signFirstLessThan() {
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    FP2Immutable x = new FP2Immutable(thresh.minus(new BIG(1)), thresh);
    assertEquals(1, x.sgn0());
  }

  @Test
  void signFirstGreaterThan() {
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    FP2Immutable x = new FP2Immutable(thresh.plus(new BIG(1)), thresh);
    assertEquals(-1, x.sgn0());
  }

  @Test
  void signSecondLessThan() {
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    FP2Immutable x = new FP2Immutable(thresh, thresh.minus(new BIG(1)));
    assertEquals(1, x.sgn0());
  }

  @Test
  void signSecondGreaterThan() {
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    FP2Immutable x = new FP2Immutable(thresh, thresh.plus(new BIG(1)));
    assertEquals(-1, x.sgn0());
  }

  @Test
  void immutabilityTest() {
    FP2Immutable foo = new FP2Immutable(ONE);
    foo.getFp2().mul(new FP2(0));
    assertEquals(ONE, foo);
  }
}
