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
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class priv_to_pub extends TestSuite {

  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void testPrivateToPublicKey(String input, String output) {
    SecretKey privateKey = SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input)));

    PublicKey publicKeyActual = new PublicKey(privateKey);
    PublicKey publicKeyExpected = PublicKey.fromBytesCompressed(Bytes.fromHexString(output));

    assertEquals(publicKeyExpected, publicKeyActual);
  }

  @MustBeClosed
  private static Stream<Arguments> readPrivateToPublicKey() throws IOException {
    return findBLSTests("**/priv_to_pub.yaml", "test_cases");
  }
}
