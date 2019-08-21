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

package tech.pegasys.artemis.reference.bls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestObject;
import pegasys.artemis.reference.TestSet;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.G2Point;

class g2_compressed extends TestSuite {

  @ParameterizedTest(name = "{index}. message hash to G2 compressed {0} -> {1}")
  @MethodSource("readMessageHashG2Compressed")
  void messageHashToG2Compressed(G2Point g2PointActual, G2Point g2PointExpected) {
    assertEquals(g2PointExpected, g2PointActual);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageHashG2Compressed() {
    Path path = Paths.get("/general/phase0/bls/msg_hash_compressed/small");
    return messageHashCompressedSetup(path);
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  public static Stream<Arguments> messageHashCompressedSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }
}
