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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.HashToCurve.hashToG2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isInG2;

import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class hashToCurveTest {

  private static final int nTest = 256;

  // Smoke test. Generate lots of hashes and make sure that they all land in G2.
  @ParameterizedTest(name = "hashToG2Test:{index}, i={0}")
  @MethodSource("getIndices")
  void hashToG2Test2(Integer i) {
    ECP2 point =
        hashToG2(
            Bytes.concatenate(Bytes.wrap("Hello, world!".getBytes(UTF_8)), Bytes.ofUnsignedInt(i)));
    assertFalse(point.is_infinity());
    assertTrue(isInG2(new JacobianPoint(point)));
  }

  public static Stream<Arguments> getIndices() {
    ArrayList<Arguments> args = new ArrayList<>();
    for (int i = 0; i < nTest; i++) {
      args.add(Arguments.of(i));
    }
    return args.stream();
  }
}
