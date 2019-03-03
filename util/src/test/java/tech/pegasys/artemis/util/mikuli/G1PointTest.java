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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class G1PointTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePoint() {
    G1Point point = G1Point.random();
    assertEquals(point, point);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalPoints() {
    G1Point point = G1Point.random();
    G1Point copyOfPoint = new G1Point(point.ecpPoint());
    assertEquals(point, copyOfPoint);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPoints() {
    G1Point point1 = G1Point.random();
    G1Point point2 = G1Point.random();
    // Ensure that we have two different points, without assuming too much about .equals
    while (point1.ecpPoint().equals(point2.ecpPoint())) {
      point2 = G1Point.random();
    }
    assertNotEquals(point1, point2);
  }

  @Test
  void succeedsWhenDefaultConstructorReturnsThePointAtInfinity() {
    G1Point infinity = new G1Point();
    assertTrue(infinity.ecpPoint().is_infinity());
    assertTrue(infinity.ecpPoint().getX().iszilch());
  }

  @Test
  void succeedsWhenSerialiseDeserialiseRoundTripWorks() {
    G1Point point1 = G1Point.random();
    G1Point point2 = G1Point.fromBytes(point1.toBytes());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenSerialiseDeserialiseCompressedRoundTripWorks() {
    G1Point point1 = G1Point.random();
    G1Point point2 = G1Point.fromBytesCompressed(point1.toBytesCompressed());
    assertEquals(point1, point2);
  }

  @Test
  void succeedsWhenDeserialisingACorrectPointDoesNotThrow() {
    String xInput =
        "0x8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertAll(() -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenDeserialisingAnIncorrectPointThrowsIllegalArgumentException() {
    String xInput =
        "0x8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde0";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXEqualToModulusThrowsIllegalArgumentException() {
    // Exactly the modulus, q
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXGreaterThanModulusThrowsIllegalArgumentException() {
    // One more than the modulus, q
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaac";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXLessThanModulusDoesNotThrowIllegalArgumentException() {
    // There's a valid X three less than the modulus. We prepend the c flag.
    String xInput =
        "0x9a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaa8";
    assertAll(() -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
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
  void succeedsWhenRoundTripDeserialiseSerialiseCompressedReturnsTheOriginalInput() {
    String xInput =
        "0x8123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    String xOutput =
        G1Point.fromBytesCompressed(Bytes.fromHexString(xInput))
            .toBytesCompressed()
            .toHexString()
            .toLowerCase();
    assertEquals(xInput, xOutput);
  }

  @Test
  void succeedsWhenDeserialiseCompressedInfinityWithTrueBFlagCreatesPointAtInfinity() {
    String xInput =
        "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    G1Point point = G1Point.fromBytesCompressed(Bytes.fromHexString(xInput));
    assertTrue(point.ecpPoint().is_infinity());
  }

  @Test
  void succeedsWhenDeserialiseCompressedInfinityWithFalseBFlagDoesNotCreatePointAtInfinity() {
    String xInput =
        "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    G1Point point = G1Point.fromBytesCompressed(Bytes.fromHexString(xInput));
    assertFalse(point.ecpPoint().is_infinity());
  }

  @Test
  void succeedsWhenSerialiseDeserialiseCompressedInfinityGivesOriginalInput() {
    G1Point point = new G1Point();
    assertEquals(point, G1Point.fromBytesCompressed(point.toBytesCompressed()));
  }

  @Test
  void succeedsWhenDeserialiseSerialiseCompressedInfinityGivesOriginalInput() {
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
  void succeedsWhenAttemptToDeserialiseWithWrongCFlagThrowsIllegalArgumentException() {
    String xInput =
        "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseWithBFlagAndXNonzeroThrowsIllegalArgumentException1() {
    String xInput =
        "0xc123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseWithBFlagAndAFlagTrueThrowsIllegalArgumentException1() {
    String xInput =
        "0xe00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    assertThrows(
        IllegalArgumentException.class,
        () -> G1Point.fromBytesCompressed(Bytes.fromHexString(xInput)));
  }
}
