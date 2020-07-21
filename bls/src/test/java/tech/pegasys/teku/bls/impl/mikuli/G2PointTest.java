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
import static tech.pegasys.teku.bls.impl.mikuli.G2Point.isInGroup;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class G2PointTest {

  @Test
  void succeedsWhenSameSeedGivesSamePoint() {
    G2Point point1 = G2Point.random(42L);
    G2Point point2 = G2Point.random(42L);
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenDifferentSeedsGiveDifferentPoints() {
    G2Point point1 = G2Point.random(1L);
    G2Point point2 = G2Point.random(2L);
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenRandomPointsAreInTheG2Subgroup() {
    for (long i = 1; i <= 20; i++) {
      G2Point point = G2Point.random(i);
      assertTrue(isInGroup(point.ecp2Point()));
    }
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePoint() {
    G2Point point = G2Point.random(42L);
    assertEquals(point, point);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalPoints() {
    G2Point point = G2Point.random(117L);
    G2Point copyOfPoint = new G2Point(point.ecp2Point());
    assertEquals(point, copyOfPoint);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPoints() {
    G2Point point1 = G2Point.random(1234L);
    G2Point point2 = G2Point.random(4321L);
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenDefaultConstructorReturnsThePointAtInfinity() {
    G2Point infinity = new G2Point();
    assertTrue(infinity.ecp2Point().is_infinity());
    assertTrue(infinity.ecp2Point().getX().iszilch());
  }

  @Test
  void succeedsWhenPointIsImmutableUnderAdd() {
    G2Point expected = G2Point.random(42L);
    G2Point actual = expected;
    G2Point test = G2Point.random(43L);
    actual.add(test); // Should not change the value of actual
    assertEquals(expected, actual);
  }

  @Test
  void succeedsWhenPointIsImmutableUnderMul() {
    G2Point expected = G2Point.random(42L);
    G2Point actual = expected;
    Scalar test = new Scalar(new BIG(2));
    actual.mul(test); // Should not change the value of actual
    assertEquals(expected, actual);
  }

  @Test
  void succeedsWhenSerializeDeserializeRoundTripWorks() {
    G2Point point1 = G2Point.random(257L);
    G2Point point2 = G2Point.fromBytes(point1.toBytes());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenSerializeDeserializeCompressedRoundTripWorks() {
    G2Point point1 = G2Point.random(513L);
    G2Point point2 = G2Point.fromBytesCompressed(point1.toBytesCompressed());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenAttemptToDeserializeXReEqualToModulusThrowsIllegalArgumentException() {
    // xRe is exactly the modulus, q, xIm is zero
    String x =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeXImEqualToModulusThrowsIllegalArgumentException() {
    // xIm is exactly the modulus, q, xRe is zero
    String x =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab"
            + "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeXReGreaterThanModulusThrowsIllegalArgumentException() {
    // xRe is the modulus plus 1, xIm is zero
    String x =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeXImGreaterThanModulusThrowsIllegalArgumentException() {
    // xIm is the modulus plus 1, xRe is zero
    String x =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac"
            + "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class, () -> G2Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenDeserializingACorrectPointDoesNotThrow() {
    String xInput =
        "0x"
            + "b2cc74bc9f089ed9764bbceac5edba416bef5e73701288977b9cac1ccb6964269d4ebf78b4e8aa7792ba09d3e49c8e6a"
            + "1351bdf582971f796bbaf6320e81251c9d28f674d720cca07ed14596b96697cf18238e0e03ebd7fc1353d885a39407e0";
    assertAll(() -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserializingAPointOnCurveButNotInG2ThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserializingAnIncorrectPointThrowsIllegalArgumentException() {
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
  void succeedsWhenRoundTripDeserializeSerializeCompressedReturnsTheOriginalInput() {
    String xInput =
        "0x"
            + "b2cc74bc9f089ed9764bbceac5edba416bef5e73701288977b9cac1ccb6964269d4ebf78b4e8aa7792ba09d3e49c8e6a"
            + "1351bdf582971f796bbaf6320e81251c9d28f674d720cca07ed14596b96697cf18238e0e03ebd7fc1353d885a39407e0";
    String xOutput =
        G2Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenDeserializeCompressedInfinityWithTrueBFlagCreatesPointAtInfinity() {
    String xInput =
        "0x"
            + "c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    G2Point point = G2Point.fromBytesCompressed(Bytes.fromHexString(xInput));
    assertTrue(point.ecp2Point().is_infinity());
  }

  @Test
  void succeedsWhenDeserializeCompressedInfinityWithFalseBFlagDoesNotCreatePointAtInfinity() {
    String xInput =
        "0x"
            + "800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenSerializeDeserializeCompressedInfinityGivesOriginalInput() {
    G2Point point = new G2Point();
    assertEquals(point, G2Point.fromBytesCompressed(point.toBytesCompressed()));
  }

  @Test
  void succeedsWhenDeserializeSerializeCompressedInfinityGivesOriginalInput() {
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
  void succeedsWhenAttemptToDeserializeWithWrongCFlagThrowsIllegalArgumentException() {
    String xInput =
        "0x"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeWithBFlagAndXNonzeroThrowsIllegalArgumentException1() {
    String xInput =
        "0x"
            + "c123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserializeWithBFlagAndAFlagTrueThrowsIllegalArgumentException1() {
    String xInput =
        "0x"
            + "e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G2Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDifferentPointsHaveDifferentHashCodes() {
    G2Point point1 = G2Point.random(42L);
    G2Point point2 = G2Point.random(43L);

    assertNotEquals(point1, point2);
    assertNotEquals(point1.hashCode(), point2.hashCode());
  }

  @Test
  void succeedsWhenTheSamePointsHaveTheSameHashCodes() {
    // Arrive at the same point in two different ways
    G2Point point1 = G2Point.random(23L);
    G2Point point2 = new G2Point(point1.ecp2Point());
    point2.add(point2);
    point1.ecp2Point().dbl();

    assertEquals(point1, point2);
    assertEquals(point1.hashCode(), point2.hashCode());
  }
}
