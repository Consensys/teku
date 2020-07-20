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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AtePairingTest {

  @Test
  void pairAndPair2AreEquivalent() {
    G1Point p1 = G1Point.random(1L);
    G2Point q2 = G2Point.random(2L);
    G1Point r1 = G1Point.random(3L);
    G2Point s2 = G2Point.random(4L);

    GTPoint expected = AtePairing.pair(p1, q2).mul(AtePairing.pair(r1, s2));
    GTPoint actual = AtePairing.pair2(p1, q2, r1, s2);

    assertEquals(expected, actual);
  }

  @Test
  void pairNoExpPlusFexpAndPairAreSame() {
    G1Point p1 = G1Point.random(1L);
    G2Point q2 = G2Point.random(2L);
    G1Point r1 = G1Point.random(3L);
    G2Point s2 = G2Point.random(4L);

    assertEquals(AtePairing.pair(p1, q2), AtePairing.fexp(AtePairing.pairNoExp(p1, q2)));
    assertEquals(
        AtePairing.pair2(p1, q2, r1, s2), AtePairing.fexp(AtePairing.pair2NoExp(p1, q2, r1, s2)));
  }
}
