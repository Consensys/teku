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

package tech.pegasys.artemis.util.mikuli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BLS12381Test {

  @Test
  void signAndVerify() {
    KeyPair keyPair = KeyPair.random(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair.secretKey(), message);
    assertTrue(BLS12381.verify(keyPair.publicKey(), message, signature));
  }

  @Test
  void signAndVerifyDifferentMessage() {
    KeyPair keyPair = KeyPair.random(117);
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair.secretKey(), message1);
    assertFalse(BLS12381.verify(keyPair.publicKey(), message2, signature));
  }

  @Test
  void signAndVerifyDifferentKeys() {
    KeyPair keyPair1 = KeyPair.random(129);
    KeyPair keyPair2 = KeyPair.random(257);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = BLS12381.sign(keyPair1.secretKey(), message);
    assertFalse(BLS12381.verify(keyPair2.publicKey(), message, signature));
  }

  @Test
  void fastAggregateVerify() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    KeyPair keyPair1 = KeyPair.random(1);
    KeyPair keyPair2 = KeyPair.random(2);
    KeyPair keyPair3 = KeyPair.random(3);

    List<PublicKey> publicKeys =
        Arrays.asList(keyPair1.publicKey(), keyPair2.publicKey(), keyPair3.publicKey());
    List<Signature> signatures =
        Arrays.asList(
            BLS12381.sign(keyPair1.secretKey(), message),
            BLS12381.sign(keyPair2.secretKey(), message),
            BLS12381.sign(keyPair3.secretKey(), message));
    Signature aggregatedSignature = BLS12381.aggregate(signatures);

    assertTrue(BLS12381.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }
}
