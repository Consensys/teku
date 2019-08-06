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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;



class priv_to_pub extends TestSuite {
  private static String testFile = "**/priv_to_pub.yaml";

  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void privateToPublicKey(
          SecretKey secretKey, PublicKey pubkeyExpected) {
    PublicKey pubkeyActual = new PublicKey(secretKey);
    assertEquals(pubkeyExpected, pubkeyActual);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readPrivateToPublicKey() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
            getParams(SecretKey.class, Arrays.asList("input")));
    arguments.add(
            getParams(PublicKey.class, Arrays.asList("output")));

    return findTests(testFile, arguments);
  }
}
