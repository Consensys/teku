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

package tech.pegasys.teku.reference.general.phase0.bls;

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
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.TestSuite;

class AggregateVerify extends TestSuite {

  @ParameterizedTest(name = "{index}. aggregateVerify {4}")
  @MethodSource("aggregateVerifyData")
  void aggregateVerify(
      List<BLSPublicKey> publicKeys,
      List<Bytes> messages,
      BLSSignature signature,
      Boolean expected,
      String testname) {

    assertEquals(expected, BLS.aggregateVerify(publicKeys, messages, signature));
  }

  @MustBeClosed
  static Stream<Arguments> aggregateVerifyData() {
    Path path = Paths.get("general/phase0/bls/aggregate_verify/small");
    return aggregateVerifySetup(path);
  }
}
