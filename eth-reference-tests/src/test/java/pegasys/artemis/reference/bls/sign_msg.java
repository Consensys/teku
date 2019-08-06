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
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class sign_msg extends TestSuite {

  @ParameterizedTest(name = "{index}. sign messages {0} -> {1}")
  @MethodSource("readSignMessages")
  void testSignMessages(LinkedHashMap<String, String> input, String output) {

    Bytes domain = Bytes.fromHexString(input.get("domain"));
    Bytes message = Bytes.fromHexString(input.get("message"));
    SecretKey privateKey =
        SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input.get("privkey"))));

    Bytes signatureActualBytes =
        BLS12381
            .sign(new KeyPair(privateKey), message.toArray(), domain)
            .signature()
            .g2Point()
            .toBytesCompressed();
    Bytes signatureExpectedBytes = Bytes.fromHexString(output);

    assertEquals(signatureExpectedBytes, signatureActualBytes);
  }

  @MustBeClosed
  private static Stream<Arguments> readSignMessages() throws IOException {
    return findBLSTests("**/sign_msg.yaml", "test_cases");
  }
}
