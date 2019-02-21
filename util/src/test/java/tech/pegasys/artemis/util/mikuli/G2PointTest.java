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

package tech.pegasys.artemis.util.mikuli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.mikuli.G2Point.scaleWithCofactor;

import net.consensys.cava.bytes.Bytes;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.junit.jupiter.api.Test;

public class G2PointTest {

  // This is the G2 cofactor as defined in the spec. It's too big for a BIG.
  private static final long[] cofactor = {
    0x05d543a95414e7f1L,
    0x091d50792876a202L,
    0xcd91de4547085abaL,
    0xa68a205b2e5a7ddfL,
    0xa628f1cb4d9e82efL,
    0x21537e293a6691aeL,
    0x1616ec6e786f0c70L,
    0xcf1c38e31c7238e5L
  };

  @Test
  void succeedsWhenIsValidReturnsTrueForARandomPoint() {
    G2Point point = G2Point.random();
    assertTrue(G2Point.isValid(point));
  }

  @Test
  void succeedsWhenSerialiseDeserialiseRoundTripWorks() {
    G2Point point1 = G2Point.random();
    G2Point point2 = G2Point.fromBytes(point1.toBytes());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenSerialiseDeserialiseCompressedRoundTripWorks() {
    G2Point point1 = G2Point.random();
    G2Point point2 = G2Point.fromBytesCompressed(point1.toBytesCompressed());
    assertEquals(point1, point2);
  }

  /** Sanity check for the scaleTestReference() reference test function */
  @Test
  void testScale2a() {
    ECP2 point = G2Point.random().ecp2Point();
    long[] factor = {0x0000000000000001};
    ECP2 scaledPoint = scaleTestReference(point, factor);
    assertTrue(point.equals(scaledPoint));
  }

  /** Sanity check for the scale() reference test function */
  @Test
  void testScale2b() {
    ECP2 point = G2Point.random().ecp2Point();
    long[] factor = {0x1100110011001100L};

    // Scale point using our routine
    ECP2 scaledPoint1 = scaleTestReference(point, factor);

    // Scale point using multiplication by BIG
    BIG scaleFactor = BIG.fromBytes(longsToBytes(factor));
    ECP2 scaledPoint2 = point.mul(scaleFactor);

    assertTrue(scaledPoint2.equals(scaledPoint1));
  }

  /** Sanity check for the scale() reference test function */
  @Test
  void testScale2c() {
    ECP2 point = G2Point.random().ecp2Point();
    long[] factor = {0x1010101010101010L, 0x0101010101010101L, 0x1100110011001100L};
    long[] factorRev = {0x1100110011001100L, 0x0101010101010101L, 0x1010101010101010L};

    // Scale point using our routine
    ECP2 scaledPoint1 = scaleTestReference(point, factorRev);

    // Scale point using multiplication by BIG
    BIG scaleFactor = BIG.fromBytes(longsToBytes(factor));
    ECP2 scaledPoint2 = point.mul(scaleFactor);

    assertTrue(scaledPoint2.equals(scaledPoint1));
  }

  @Test
  void compareScalingByCofactorResults() {
    ECP2 point = G2Point.random().ecp2Point();

    // Scale point using scale2()
    ECP2 scaledPoint1 = scaleTestReference(point, cofactor);

    // Scale point using scaleWithCofactor()
    ECP2 scaledPoint2 = scaleWithCofactor(point);

    assertTrue(scaledPoint2.equals(scaledPoint1));
  }

  /**
   * The data here comes from the reference Eth2 reference BLS tests
   * https://github.com/ethereum/eth2.0-tests/blob/df7888e658943d3e733f660bb7ace7d829d70011/test_vectors/test_bls.yml
   * It's the first test-case. We should find a way to automate this. Note that these test cases are
   * out of date, and will need need updating when we start passing the hash of the message rather
   * than the message.
   */
  @Test
  void testHashToG2() {
    // TODO: Update to latest spec when we have new test cases
    Bytes message = Bytes.fromHexString("0x6d657373616765");
    G2Point point = G2Point.hashToG2(message, 0L);

    String[] testCasesResult = {
      "0x0e34a428411c115e094b51afa596b0e594fb325dfe42d481a87a1e89ab35f531aadc7b4f8eb5ce9d3973d2cfef8f20fd",
      "0x12830258cc04219871cd71eb9478eb1f1971104bcf49ac60ec6e3368e9047e10acef61b75d803849942bea06e3bc99a8",
      "0x162f48744b91343105f5b1830f3346da815ada6b615afc57c611b423470fb53d26c9e8a1e6288b524f75a8e69492cd31",
      "0x129b6b431d1d3dabda12739eadac269e7d85e2940f270b5486bfddf2c36109164dd20ba5369e9305ef16e470d0eafad0",
      "0x05825be2264001369d47bfac0fef4735d4cbbc8e6e0cd2f25b68948122b94a856473f07b9e6d16af7a7286bab41fc9d6",
      "0x0a2fba34dddfa47d0363fdf88d6d7fdb9db3d914f70275ea6923b3fceffee565dd7de1b2109293a72139bf3b82126a52"
    };

    ECP2 expected = makePoint(testCasesResult);
    assertTrue(point.ecp2Point().equals(expected));
  }

  /**
   * Hacky conversion of array of long to array of 48 bytes for input into BIG.fromBytes()
   *
   * @param longs an array of upto 6 longs
   * @return an array of 48 bytes
   */
  private static byte[] longsToBytes(long[] longs) {
    Bytes bytes = Bytes.EMPTY;
    for (long w : longs) {
      bytes = Bytes.concatenate(Bytes.ofUnsignedLong(w), bytes);
    }
    bytes = Bytes.concatenate(Bytes.wrap(new byte[48 - 8 * longs.length]), bytes);
    return bytes.toArray();
  }

  /**
   * Utility for converting test case data to a point with Z = 1 (the affine transformation)
   *
   * @param coords
   * @return
   */
  private static ECP2 makePoint(String[] coords) {
    BIG xRe = BIG.fromBytes(Bytes.fromHexString(coords[0]).toArray());
    BIG xIm = BIG.fromBytes(Bytes.fromHexString(coords[1]).toArray());
    BIG yRe = BIG.fromBytes(Bytes.fromHexString(coords[2]).toArray());
    BIG yIm = BIG.fromBytes(Bytes.fromHexString(coords[3]).toArray());
    BIG zRe = BIG.fromBytes(Bytes.fromHexString(coords[4]).toArray());
    BIG zIm = BIG.fromBytes(Bytes.fromHexString(coords[5]).toArray());

    FP2 x = new FP2(xRe, xIm);
    FP2 y = new FP2(yRe, yIm);
    FP2 z = new FP2(zRe, zIm);

    FP2 one = new FP2(1);
    if (z.equals(one)) {
      x.reduce();
      y.reduce();
      return new ECP2(x, y);
    } else {
      z.inverse();
      x.mul(z);
      x.reduce();
      y.mul(z);
      y.reduce();
      return new ECP2(x, y);
    }
  }

  /**
   * Multiply the point by the scaling factor. Used for multiplying by the group cofactor.
   *
   * <p>This uses long-multiplication with 64-bit words as the digits. It's not quick, but is
   * intuitive and serves as a reference test for scaleWithCofactor().
   *
   * @param point the point to be scaled
   * @param factor the scaling factor as an array of long
   */
  static ECP2 scaleTestReference(ECP2 point, long[] factor) {
    Bytes padding = Bytes.wrap(new byte[40]);
    ECP2 sum = new ECP2();
    sum.inf(); // This is zero

    byte[] foo = new byte[48];
    foo[39] = 1;
    BIG twoTo64 = BIG.fromBytes(foo);

    for (long w : factor) {
      sum = sum.mul(twoTo64);
      ECP2 tmp = new ECP2(point);

      byte[] bar = Bytes.concatenate(padding, Bytes.ofUnsignedLong(w)).toArray();
      BIG big = BIG.fromBytes(bar);
      big.norm();
      tmp = tmp.mul(big);

      sum.add(tmp);
    }
    return sum;
  }

  // TODO: tests for equal/not equal
}
