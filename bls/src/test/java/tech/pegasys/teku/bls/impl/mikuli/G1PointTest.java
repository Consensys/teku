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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.G1Point.isInGroup;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class G1PointTest {

  @Test
  void succeedsWhenSameSeedGivesSamePoint() {
    G1Point point1 = G1Point.random(42L);
    G1Point point2 = G1Point.random(42L);
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenDifferentSeedsGiveDifferentPoints() {
    G1Point point1 = G1Point.random(1L);
    G1Point point2 = G1Point.random(2L);
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenRandomPointsAreInTheG1Subgroup() {
    for (long i = 1; i <= 20; i++) {
      G1Point point = G1Point.random(i);
      assertTrue(isInGroup(point.ecpPoint()));
    }
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePoint() {
    G1Point point = G1Point.random(65L);
    assertEquals(point, point);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalPoints() {
    G1Point point = G1Point.random(129L);
    G1Point copyOfPoint = new G1Point(point.ecpPoint());
    assertEquals(point, copyOfPoint);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPoints() {
    G1Point point1 = G1Point.random(42L);
    G1Point point2 = G1Point.random(43L);
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenDefaultConstructorReturnsThePointAtInfinity() {
    G1Point infinity = new G1Point();
    assertTrue(infinity.ecpPoint().is_infinity());
    assertTrue(infinity.ecpPoint().getX().iszilch());
  }

  @Test
  void succeedsWhenPointIsImmutableUnderNeg() {
    G1Point expected = G1Point.random(42L);
    G1Point actual = expected;
    actual.neg(); // Should not change the value of actual
    assertEquals(expected, actual);
  }

  @Test
  void succeedsWhenPointIsImmutableUnderAdd() {
    G1Point expected = G1Point.random(42L);
    G1Point actual = expected;
    G1Point test = G1Point.random(43L);
    actual.add(test); // Should not change the value of actual
    assertEquals(expected, actual);
  }

  @Test
  void succeedsWhenPointIsImmutableUnderMul() {
    G1Point expected = G1Point.random(42L);
    G1Point actual = expected;
    Scalar test = new Scalar(new BIG(2));
    actual.mul(test); // Should not change the value of actual
    assertEquals(expected, actual);
  }

  @Test
  void succeedsWhenSerializeDeserializeRoundTripWorks() {
    G1Point point1 = G1Point.random(257L);
    G1Point point2 = G1Point.fromBytes(point1.toBytes());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenSerializeDeserializeCompressedRoundTripWorks() {
    G1Point point1 = G1Point.random(513L);
    G1Point point2 = G1Point.fromBytesCompressed(point1.toBytesCompressed());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenDeserializingACorrectPointDoesNotThrow() {
    String xInput =
        "0xa491d1b0ecd9bb917989f0e74f0dea0422eac4a873e5e2644f368dffb9a6e20fd6e10c1b77654d067c0618f6e5a7f79a";
    assertAll(() -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserializingAPointOnCurveButNotInG1ThrowsIllegalArgumentException() {
    String xInput =
        "0x8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserializingAnIncorrectPointThrowsIllegalArgumentException() {
    String xInput =
        "0x8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde0";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeXEqualToModulusThrowsIllegalArgumentException() {
    // Exactly the modulus, q
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeXGreaterThanModulusThrowsIllegalArgumentException() {
    // One more than the modulus, q
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenProvidingTooFewBytesToFromBytesCompressedThrowsIllegalArgumentException() {
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenProvidingTooManyBytesToFromBytesCompressedThrowsIllegalArgumentException() {
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaa900";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenRoundTripDeserializeSerializeCompressedReturnsTheOriginalInput() {
    String xInput =
        "0xb301803f8b5ac4a1133581fc676dfedc60d891dd5fa99028805e5ea5b08d3491af75d0707adab3b70c6a6a580217bf81";
    String xOutput =
        G1Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenDeserializeCompressedInfinityWithTrueBFlagCreatesPointAtInfinity() {
    String xInput =
        "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    G1Point point = G1Point.fromBytesCompressed(Bytes.fromHexString(xInput));
    assertTrue(point.ecpPoint().is_infinity());
  }

  @Test
  void succeedsWhenDeserializeCompressedInfinityWithFalseBFlagDoesNotCreatePointAtInfinity() {
    String xInput =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenSerializeDeserializeCompressedInfinityGivesOriginalInput() {
    G1Point point = new G1Point();
    assertEquals(point, G1Point.fromBytesCompressed(point.toBytesCompressed()));
  }

  @Test
  void succeedsWhenDeserializeSerializeCompressedInfinityGivesOriginalInput() {
    String xInput =
        "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    String xOutput =
        G1Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenAttemptToDeserializeWithWrongCFlagThrowsIllegalArgumentException() {
    String xInput =
        "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeWithBFlagAndXNonzeroThrowsIllegalArgumentException1() {
    String xInput =
        "0xc123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeWithBFlagAndAFlagTrueThrowsIllegalArgumentException1() {
    String xInput =
        "0xe00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDifferentPointsHaveDifferentHashCodes() {
    G1Point point1 = G1Point.random(1234L);
    G1Point point2 = G1Point.random(4321L);
    assertNotEquals(point1, point2);
    assertNotEquals(point1.hashCode(), point2.hashCode());
  }

  @Test
  void succeedsWhenTheSamePointsHaveTheSameHashCodes() {
    // Arrive at the same point in two different ways
    G1Point point1 = G1Point.random(1025L);
    G1Point point2 = new G1Point(point1.ecpPoint());
    point2.add(point2);
    point1.ecpPoint().dbl();

    assertEquals(point1, point2);
    assertEquals(point1.hashCode(), point2.hashCode());
  }
}
