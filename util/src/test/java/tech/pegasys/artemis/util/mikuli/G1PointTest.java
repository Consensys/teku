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
import static tech.pegasys.artemis.util.mikuli.G1Point.isValid;

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
  void succeedsWhenIsValidReturnsTrueForARandomPoint() {
    G1Point point = G1Point.random();
    assertTrue(G1Point.isValid(point));
  }

  @Test
  void succeedsWhenPointWithCFalseIsInvalid() {
    G1Point point = G1Point.random();
    // C1 should always be true
    assertFalse(isValid(point.ecpPoint(), point.getA(), point.getB(), false));
  }

  @Test
  void succeedsWhenPointWithBTrueIsInvalid() {
    G1Point point = G1Point.random();
    // B1 is true only for the point at infinity
    assertFalse(isValid(point.ecpPoint(), point.getA(), true, true));
  }

  @Test
  void succeedsWhenPointWithAInvertedIsInvalid() {
    G1Point point = G1Point.random();
    assertFalse(isValid(point.ecpPoint(), !point.getA(), false, true));
  }

  @Test
  void succeedsWhenPointAtInfinityHasCorrectFlags() {
    G1Point infinity = new G1Point();
    assertTrue(infinity.ecpPoint().is_infinity());
    assertFalse(infinity.getA());
    assertTrue(infinity.getB());
    assertTrue(infinity.getC());
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
  void succeedsWhenAttemptToDeserialiseXEqualToModulusThrowsIllegalArgumentException() {
    // Exactly the modulus, q
    String x =
        "0x01a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaab";
    assertThrows(
        IllegalArgumentException.class, () -> G1Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXGreaterThanModulusThrowsIllegalArgumentException() {
    // One more than the modulus, q
    String x =
        "0x01a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaac";
    assertThrows(
        IllegalArgumentException.class, () -> G1Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }

  @Test
  void succeedsWhenAttemptToDeserialiseXLessThanModulusDoesNotThrowIllegalArgumentException() {
    // There's a valid X two less than the modulus. We prepend the c flag.
    String x =
        "0x81a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa9";
    assertAll(() -> G1Point.fromBytesCompressed(Bytes.fromHexString(x)));
  }
}
