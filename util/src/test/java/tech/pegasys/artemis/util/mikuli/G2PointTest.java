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

import org.junit.jupiter.api.Test;

public class G2PointTest {

  @Test
  void succeedsWhenIsValidReturnsTrueForARandomPoint() {
    G2Point point = G2Point.random();
    assertTrue(G2Point.isValid(point));
  }

  @Test
  void succeedsWhenSerialiseDeserialiseRoundTripWorks() {
    G2Point point1 = G2Point.random();
    G2Point point2 = G2Point.fromBytes(point1.toBytes());
    assertTrue(point1.equals(point2));
  }

  @Test
  void succeedsWhenSerialiseDeserialiseCompressedRoundTripWorks() {
    G2Point point1 = G2Point.random();
    G2Point point2 = G2Point.fromBytesCompressed(point1.toBytesCompressed());
    assertEquals(point1, point2);
  }

  // TODO: tests for equal/not equal
}
