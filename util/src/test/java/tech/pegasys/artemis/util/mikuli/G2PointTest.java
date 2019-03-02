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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.mikuli.G2Point.normaliseY;
import static tech.pegasys.artemis.util.mikuli.G2Point.scaleWithCofactor;

import net.consensys.cava.bytes.Bytes;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.junit.jupiter.api.Test;

class G2PointTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePoint() {
    G2Point point = G2Point.random();
    assertEquals(point, point);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalPoints() {
    G2Point point = G2Point.random();
    G2Point copyOfPoint = new G2Point(point.ecp2Point());
    assertEquals(point, copyOfPoint);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPoints() {
    G2Point point1 = G2Point.random();
    G2Point point2 = G2Point.random();
    // Ensure that we have two different points, without assuming too much about .equals
    while (point1.ecp2Point().equals(point2.ecp2Point())) {
      point2 = G2Point.random();
    }
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenDefaultConstructorReturnsThePointAtInfinity() {
    G2Point infinity = new G2Point();
    assertTrue(infinity.ecp2Point().is_infinity());
    assertTrue(infinity.ecp2Point().getX().iszilch());
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

  // Sanity check for the scaleTestReference1() reference test function
  @Test
  void succeedsWhenScalingReferenceTestCorrectlyMultipliesByOne() {
    G2Point point = G2Point.random();
    long[] factor = {0x0000000000000001L};
    G2Point scaledPoint = scaleTestReference1(point, factor);
    assertEquals(point, scaledPoint);
  }

  // Sanity check for the scale() reference test function
  @Test
  void succeedsWhenScalingReferenceTestCorrectlyMultipliesByLong() {
    G2Point point = G2Point.random();
    long[] factor = {0xcf1c38e31c7238e5L};

    // Scale point using our routine
    G2Point scaledPoint1 = scaleTestReference1(point, factor);

    // Scale point using multiplication by BIG
    Scalar scaleFactor = new Scalar(longsToBig(factor));
    G2Point scaledPoint2 = point.mul(scaleFactor);

    assertEquals(scaledPoint2, scaledPoint1);
  }

  // Sanity check for the scale() reference test function
  @Test
  void succeedsWhenScalingReferenceTestCorrectlyMultipliesByArrayOfLong() {
    G2Point point = G2Point.random();
    long[] factor = {0xf0f0f0f0f0f0f0f0L, 0x0f0f0f0f0f0f0f0fL, 0xaa00aa00aa00aa00L};
    long[] factorRev = {0xaa00aa00aa00aa00L, 0x0f0f0f0f0f0f0f0fL, 0xf0f0f0f0f0f0f0f0L};

    // Scale point using our routine
    G2Point scaledPoint1 = scaleTestReference1(point, factorRev);

    // Scale point using multiplication by BIG
    Scalar scaleFactor = new Scalar(longsToBig(factor));
    G2Point scaledPoint2 = point.mul(scaleFactor);

    assertEquals(scaledPoint2, scaledPoint1);
  }

  // Sanity check for the scale() reference test functions
  @Test
  void succeedsWhenScalingReferenceTestsAgreeForLong() {
    G2Point point = G2Point.random();
    long[] factor = {0xcf1c38e31c7238e5L};

    // Scale point using our long multiplication routine
    G2Point scaledPoint1 = scaleTestReference1(point, factor);

    // Scale point using our repeated doubling routine
    G2Point scaledPoint2 = scaleTestReference2(point, factor);

    assertEquals(scaledPoint2, scaledPoint1);
  }

  // Sanity check for the scale() reference test functions
  @Test
  void succeedsWhenScalingReferenceTestsAgreeForCofactor() {
    G2Point point = G2Point.random();

    // Scale point using our long multiplication routine
    G2Point scaledPoint1 = scaleTestReference1(point, cofactor);

    // Scale point using our repeated doubling routine
    G2Point scaledPoint2 = scaleTestReference2(point, cofactor);

    assertEquals(scaledPoint2, scaledPoint1);
  }

  @Test
  void succeedsWhenScalingByCofactorAgreesWithTheReferenceTest() {
    G2Point point = G2Point.random();

    // Scale point using scale2()
    G2Point scaledPoint1 = scaleTestReference2(point, cofactor);

    // Scale point using scaleWithCofactor()
    G2Point scaledPoint2 = new G2Point(scaleWithCofactor(point.ecp2Point()));

    assertEquals(scaledPoint1, scaledPoint2);
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXReEqualToModulusThrowsIllegalArgumentException() {
    // xRe is exactly the modulus, q, xIm is zero
    String x =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXImEqualToModulusThrowsIllegalArgumentException() {
    // xIm is exactly the modulus, q, xRe is zero
    String x =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab"
            + "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXReGreaterThanModulusThrowsIllegalArgumentException() {
    // xRe is the modulus plus 1, xIm is zero
    String x =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXImGreaterThanModulusThrowsIllegalArgumentException() {
    // xIm is the modulus plus 1, xRe is zero
    String x =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac"
            + "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenDeserialisingACorrectPointDoesNotThrow() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertAll(() -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserialisingAnIncorrectPointThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde0";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenProvidingTooFewBytesToFromBytesCompressedThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenProvidingTooManyBytesToFromBytesCompressedThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdefff";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenRoundTripDeserialiseSerialiseCompressedReturnsTheOriginalInput() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    String xOutput =
        G2Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenDeserialiseCompressedInfinityWithTrueBFlagCreatesPointAtInfinity() {
    String xInput =
        "0x"
            + "c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    G2Point point = G2Point.fromBytesCompressed(Bytes.fromHexString(xInput));
    assertTrue(point.ecp2Point().is_infinity());
  }

  @Test
  void succeedsWhenDeserialiseCompressedInfinityWithFalseBFlagDoesNotCreatePointAtInfinity() {
    String xInput =
        "0x"
            + "800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenSerialiseDeserialiseCompressedInfinityGivesOriginalInput() {
    G2Point point = new G2Point();
    assertEquals(point, G2Point.fromBytesCompressed(point.toBytesCompressed()));
  }

  @Test
  void succeedsWhenDeserialiseSerialiseCompressedInfinityGivesOriginalInput() {
    String xInput =
        "0x"
            + "c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    String xOutput =
        G2Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenAttemptToDeserialiseWithWrongCFlagThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseWithBFlagAndXNonzeroThrowsIllegalArgumentException1() {
    String xInput =
        "0x"
            + "c123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseWithBFlagAndAFlagTrueThrowsIllegalArgumentException1() {
    String xInput =
        "0x"
            + "e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  /*
   * The data in the tests below is based on the reference Eth2 reference BLS tests
   * https://github.com/ethereum/eth2.0-tests/blob/df7888e658943d3e733f660bb7ace7d829d70011/test_vectors/test_bls.yml
   * The tests have since changed, and are now automated, but these remain here for reference.
   */

  @Test
  void succeedsWhenHashToG2MatchesTestDataCase1() {
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

    G2Point expected = makePoint(testCasesResult);
    assertEquals(expected, point);
  }

  @Test
  void succeedsWhenHashToG2MatchesTestDataCase2() {
    Bytes message = Bytes.fromHexString("0x6d657373616765");
    G2Point point = G2Point.hashToG2(message, 1L);

    String[] testCasesResult = {
      "0x0c4efb2057400f7316bdfd6a89aa3afd34411b045e81bc75fa7f6a6bc5736f6528ceb5857c04866b98a43f6fdf08037c",
      "0x1539234325ccfd75fda86b1acd449af4a954b0ce45840c4a1e7596f41a99d12735b69968ebe998b4379aa3c3cdc9a8c4",
      "0x05488381cf53bd9f750451eb40c6bdd04b86689b8b6374a8df30c7d1a2ecc4a33dbe4f13ce7ed6f7e21c123480c4e959",
      "0x180d467a582e8fd8e766ed90d9a993ae22503fa0358af72ba405350f7e97b910fc2f849a77f0b9ba869fec3d65c4331a",
      "0x143c969735b29ecb356f2406d001d220d54c3e90d254769a350e7da60de287d4a89ca6dc1bff53e825fa6b889a0801b5",
      "0x048daeee78cefb03a26e382c7dd582d61a873461b3b1b79c3dc5706cac133666b1cc3b923fed6de46fb63f399d985a1c"
    };

    G2Point expected = makePoint(testCasesResult);
    assertEquals(expected, point);
  }

  @Test
  void succeedsWhenHashToG2MatchesTestDataCase3() {
    Bytes message =
        Bytes.fromHexString(
            "0x"
                + "56657279202e2e2e2e2e2e2e2e2e2e2e2e2e2e206c6f6e67202e2e2e2e2e2e2e"
                + "2e2e2e2e2e2e206d657373616765202e2e2e2e207769746820656e74726f7079"
                + "3a20313233343536373839302d626561636f6e2d636861696e");
    G2Point point = G2Point.hashToG2(message, 0xffffffffL);

    String[] testCasesResult = {
      "0x1735fa1eeb8f5927bfbd50497a0f5d0dda9b77e044bbdc2305ad4fed35a2e7fad2f97aa43a0c25e19741481acf836973",
      "0x06a71fb5c75ca78fefe799c8951e24fde9feb58f07e0da3165a73c40f6cd48eb7d82f6d95c18e3c10abd4e293d3ec6b3",
      "0x0a852998b4535f26f3dd91af9d0ec9a19cf692ed90523f288f5600cbc8c1a6694d9525b714af12e55ee02ba408ba451a",
      "0x015a8507b42edd62bd82a12a59bdb9b9ae4b2c53c94bcadaa693a8c0c920718e035f849fd70e5d4c74cce782d3eb2096",
      "0x1422202b89e51c324096d038174fdf2f1671b09ee9315054d000b500db4c992de5aa9b333a54d1b1cfc73069ee634c14",
      "0x0af76bfd57821e779b7f2fbed3b314c5b3407be1b4a703396c2afa95e6465b46881b459aca082e72a4613b31b98f7467"
    };

    G2Point expected = makePoint(testCasesResult);
    assertEquals(expected, point);
  }

  /* ==== Helper Functions ===================================================================== */

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

  /**
   * Hacky conversion of array of long to array of 48 bytes for input into BIG.fromBytes()
   *
   * @param longs an array of upto 6 longs
   * @return the data in the longs as an array of 48 bytes
   */
  private static BIG longsToBig(long[] longs) {
    Bytes bytes = Bytes.EMPTY;
    for (long w : longs) {
      bytes = Bytes.concatenate(Bytes.ofUnsignedLong(w), bytes);
    }
    bytes = Bytes.concatenate(Bytes.wrap(new byte[48 - 8 * longs.length]), bytes);
    BIG newBig = BIG.fromBytes(bytes.toArray());
    newBig.norm();
    return newBig;
  }

  /**
   * Utility for converting uncompressed test case data to a point
   *
   * <p>The test case data is not in standard form (Z = 1). This routine converts the input to a
   * point and applies the affine transformation. This routine is for uncompressed input.
   *
   * @param coords an array of strings {xRe, xIm, yRe, yIm, zRe, zIm}
   * @return the point corresponding to the input
   */
  private static G2Point makePoint(String[] coords) {
    BIG xRe = BIG.fromBytes(Bytes.fromHexString(coords[0]).toArray());
    BIG xIm = BIG.fromBytes(Bytes.fromHexString(coords[1]).toArray());
    BIG yRe = BIG.fromBytes(Bytes.fromHexString(coords[2]).toArray());
    BIG yIm = BIG.fromBytes(Bytes.fromHexString(coords[3]).toArray());
    BIG zRe = BIG.fromBytes(Bytes.fromHexString(coords[4]).toArray());
    BIG zIm = BIG.fromBytes(Bytes.fromHexString(coords[5]).toArray());

    FP2 x = new FP2(xRe, xIm);
    FP2 y = new FP2(yRe, yIm);
    FP2 z = new FP2(zRe, zIm);

    // Normalise the point (affine transformation) so that Z = 1
    z.inverse();
    x.mul(z);
    x.reduce();
    y.mul(z);
    y.reduce();

    // It should not be necessary to normaliseY here: is the test case wrong, or have I got
    // normaliseY() implementation wrong?
    // Turns out to be a bug in py_ecc: https://github.com/ethereum/eth2.0-specs/issues/508
    // TODO: Check that this is fixed in the test suite sometime and remove normaliseY when it is.
    return new G2Point(normaliseY(new ECP2(x, y)));
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
  private static G2Point scaleTestReference1(G2Point point, long[] factor) {
    Bytes padding = Bytes.wrap(new byte[40]);
    ECP2 sum = new ECP2();

    byte[] foo = new byte[48];
    foo[39] = 1;
    BIG twoTo64 = BIG.fromBytes(foo);

    for (long w : factor) {
      sum = sum.mul(twoTo64);
      ECP2 tmp = new ECP2(point.ecp2Point());

      byte[] bar = Bytes.concatenate(padding, Bytes.ofUnsignedLong(w)).toArray();
      BIG big = BIG.fromBytes(bar);
      big.norm();
      tmp = tmp.mul(big);

      sum.add(tmp);
    }
    return new G2Point(sum);
  }

  /**
   * Multiply the point by the scaling factor. Used for multiplying by the group cofactor.
   *
   * <p>This uses repeated doubling and addition, similar to the ZCash implementation. It's not
   * quick, but serves as a reference test for scaleWithCofactor().
   *
   * @param point the point to be scaled
   * @param factor the scaling factor as an array of long
   */
  private static G2Point scaleTestReference2(G2Point point, long[] factor) {
    ECP2 tmp = new ECP2(point.ecp2Point());
    ECP2 sum = new ECP2();
    sum.inf(); // This is zero

    for (int i = factor.length - 1; i >= 0; i--) {
      long w = factor[i];
      for (int j = 0; j < Long.SIZE; j++) {
        if (Long.remainderUnsigned(w, 2) == 1) {
          sum.add(tmp);
        }
        w = Long.divideUnsigned(w, 2);
        tmp.dbl();
      }
    }
    return new G2Point(sum);
  }
}
