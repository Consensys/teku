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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.artemis.util.mikuli.Signature;

class sign_msg extends TestSuite {
  private static String testFile = "**/sign_msg.yaml";

  @ParameterizedTest(name = "{index}. sign messages {0} -> {1}")
  @MethodSource("readSignMessages")
  void signMessages(Bytes message, Bytes domain, SecretKey secretKey, Signature signatureExpected) {
    Signature signatureActual =
        BLS12381.sign(new KeyPair(secretKey), message.toArray(), domain).signature();
    assertEquals(signatureExpected, signatureActual);
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readSignMessages() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Bytes.class, Arrays.asList("input", "message")));
    arguments.add(getParams(Bytes.class, Arrays.asList("input", "domain")));
    arguments.add(getParams(SecretKey.class, Arrays.asList("input", "privkey")));
    arguments.add(getParams(Signature.class, Arrays.asList("output")));

    return findTests(testFile, arguments);
  }
}
