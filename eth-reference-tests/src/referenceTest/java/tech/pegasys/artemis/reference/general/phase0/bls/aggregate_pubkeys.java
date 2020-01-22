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
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

@ExtendWith(BouncyCastleExtension.class)
class aggregate_pubkeys extends TestSuite {

  // The aggregate_pubkeys handler should aggregate the keys in the input, and the result should
  // match the expected output.

  // TODO - this test disabled pending test suite update to match the new BLS interfaces in the
  // spec.
  @Disabled
  @ParameterizedTest(name = "{index}. aggregate pub keys {0} -> {1}")
  @MethodSource("readAggregatePublicKeys")
  void aggregatePubkeys(List<BLSPublicKey> pubkeys, BLSPublicKey aggregatePubkeyExpected) {
    BLSPublicKey aggregatePubkeyActual = BLSPublicKey.random(1);
    assertEquals(aggregatePubkeyExpected, aggregatePubkeyActual);
  }

  @MustBeClosed
  static Stream<Arguments> readAggregatePublicKeys() {
    Path path = Paths.get("general", "phase0", "bls", "aggregate_pubkeys", "small", "agg_pub_keys");
    return aggregatePublicKeysSetup(path);
  }
}
