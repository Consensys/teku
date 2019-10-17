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

package tech.pegasys.artemis.reference.general.phase0.bls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.Signature;

class aggregate_sigs extends TestSuite {

  // The aggregate_sigs handler should aggregate the signatures in the input, and the result should
  // match the expected output.
  @ParameterizedTest(name = "{index}. aggregate sigs {0} -> {1}")
  @MethodSource("readAggregateSignatures")
  void aggregateSig(List<Signature> signatures, Bytes aggregateSignatureExpected) {
    Bytes aggregateSignatureActual = Signature.aggregate(signatures).g2Point().toBytesCompressed();
    assertEquals(aggregateSignatureExpected, aggregateSignatureActual);
  }

  @MustBeClosed
  static Stream<Arguments> readAggregateSignatures() {
    Path path = Paths.get("general", "phase0", "bls", "aggregate_sigs", "small");
    return aggregateSignaturesSetup(path);
  }
}
