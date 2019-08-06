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

import com.google.errorprone.annotations.MustBeClosed;
import kotlin.Pair;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.PublicKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
class aggregate_pubkeys extends TestSuite {
  private static String testFile = "**/aggregate_pubkeys.yaml";

  @ParameterizedTest(name = "{index}. aggregate pub keys {0} -> {1}")
  @MethodSource("readAggregatePubKeys")
  void aggregatePubkeys(
          List<PublicKey> pubkeys, PublicKey aggregatePubkeyExpected) {
    PublicKey aggregatePubkeyActual = PublicKey.aggregate(pubkeys);
    assertEquals(aggregatePubkeyExpected, aggregatePubkeyActual);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readAggregatePubKeys() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
            getParams(PublicKey[].class, Arrays.asList("input")));
    arguments.add(
            getParams(PublicKey.class, Arrays.asList("output")));

    return findTests(testFile, arguments);
  }
}