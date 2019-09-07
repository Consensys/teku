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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class priv_to_pub extends TestSuite {

  // The priv_to_pub handler should compute the public key for the given private key input, and the
  // result should match the expected output.
  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void privateToPublicKey(SecretKey secretKey, PublicKey pubkeyExpected) {
    PublicKey pubkeyActual = new PublicKey(secretKey);
    assertEquals(pubkeyExpected, pubkeyActual);
  }

  @MustBeClosed
  static Stream<Arguments> readPrivateToPublicKey() {
    Path path = Paths.get("/general/phase0/bls/priv_to_pub/small");
    return privateKeyPublicKeySetup(path);
  }
}
