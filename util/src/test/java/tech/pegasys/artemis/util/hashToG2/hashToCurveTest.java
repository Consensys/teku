/*
 * Copyright 2020 ConsenSys AG.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.util.hashToG2.Chains.qChain;
import static tech.pegasys.artemis.util.hashToG2.hashToCurve.hashToG2;

import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class hashToCurveTest {

  // Smoke test. Generate lots of hashes and make sure that they all land in G2.
  @Test
  void hashToG2Test() {
    for (int i = 0; i < 256; i++) {
      ECP2 point =
          hashToG2(
              Bytes.concatenate(
                  Bytes.wrap("Hello, world!".getBytes(UTF_8)), Bytes.ofUnsignedInt(i)));
      assertFalse(point.is_infinity());
      assertTrue(qChain(new JacobianPoint(point)).isInfinity());
    }
  }
}
