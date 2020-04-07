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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.bls.bls.BLS;
import tech.pegasys.artemis.bls.bls.BLSSignature;

class Aggregate extends TestSuite {

  @ParameterizedTest(name = "{index}. aggregate {2}")
  @MethodSource("aggregateData")
  void aggregate(
      List<BLSSignature> signatures, BLSSignature aggregateSignatureExpected, String testname) {
    BLSSignature aggregateSignatureActual = BLS.aggregate(signatures);
    assertEquals(aggregateSignatureExpected, aggregateSignatureActual);
  }

  @MustBeClosed
  static Stream<Arguments> aggregateData() {
    Path path = Paths.get("general/phase0/bls/aggregate/small");
    return aggregateSetup(path);
  }
}
