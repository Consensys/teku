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

package pegasys.artemis.reference.general.phase0.bls;

import com.google.errorprone.annotations.MustBeClosed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.G2Point;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class g2_uncompressed extends TestSuite {

  //The msg_hash_g2_uncompressed handler should hash the message, with the given domain, to G2, without compression, and the result should match the expected output.
  @ParameterizedTest(name = "{index}. message hash to G2 uncompressed {0} -> {1}")
  @MethodSource("readMessageHashG2Uncompressed")
  void messageHashToG2Uncompressed(G2Point g2PointExpected, G2Point g2PointActual) {
    assertEquals(g2PointExpected, g2PointActual);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageHashG2Uncompressed() {
    Path path = Paths.get("/general/phase0/bls/msg_hash_uncompressed/small");
    return messageHashUncompressedSetup(path);
  }
}
