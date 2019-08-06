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

package pegasys.artemis.reference.bls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.Signature;

class aggregate_sigs extends TestSuite {

  @ParameterizedTest(name = "{index}. aggregate sig {0} -> {1}")
  @MethodSource("readAggregateSigs")
  void testAggregateSig(ArrayList<String> input, String output) {

    ArrayList<Signature> signatures = new ArrayList<>();
    for (String sig : input) {
      signatures.add(Signature.fromBytesCompressed(Bytes.fromHexString(sig)));
    }

    Bytes aggregateSignatureActualBytes =
        Signature.aggregate(signatures).g2Point().toBytesCompressed();
    Bytes aggregateSignatureExpectedBytes = Bytes.fromHexString(output);

    assertEquals(aggregateSignatureExpectedBytes, aggregateSignatureActualBytes);
  }

  @MustBeClosed
  private static Stream<Arguments> readAggregateSigs() throws IOException {
    return findBLSTests("**/aggregate_sigs.yaml", "test_cases");
  }
}
