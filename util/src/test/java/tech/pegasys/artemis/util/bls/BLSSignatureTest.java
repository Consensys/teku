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

package tech.pegasys.artemis.util.bls;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class BLSSignatureTest {

  @Test
  void succeedsIfEmptySignatureIsCorrectlyFormed() {
    BLSSignature emptySignature = BLSSignature.empty();
    assertTrue(emptySignature.isEmpty());
    // SSZ prepends the length as four little-endian bytes
    assertEquals(
        "0x60000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000",
        emptySignature.toBytes().toHexString());
  }

  @Test
  void succeedsIfValidSignatureIsNotEmpty() {
    BLSSignature signature = BLSSignature.random();
    assertTrue(!signature.isEmpty());
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    BLSSignature signature = BLSSignature.random();
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalSignatures() {
    BLSSignature signature = BLSSignature.random();
    BLSSignature copyOfSignature = new BLSSignature(signature.getSignature());
    assertEquals(signature, copyOfSignature);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    BLSSignature signature1 = BLSSignature.random();
    BLSSignature signature2 = BLSSignature.random();
    // Ensure that we have two different signatures, without assuming too much about .equals
    while (signature1.getSignature().equals(signature2.getSignature())) {
      signature2 = BLSSignature.random();
    }
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenAMessageSignsAndVerifies() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    long domain = 42;
    BLSSignature signature = BLSSignature.sign(keyPair, message, domain);
    assertTrue(signature.checkSignature(keyPair.publicKeyAsBytes(), message, domain));
  }

  @Test
  void succeedsWhenVerifyingDifferentDomainFails() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    long domain1 = 42;
    long domain2 = 43;
    BLSSignature signature = BLSSignature.sign(keyPair, message, domain1);
    assertFalse(signature.checkSignature(keyPair.publicKeyAsBytes(), message, domain2));
  }

  @Test
  void succeedsWhenVerifyingDifferentMessageFails() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    long domain = 42;
    BLSSignature signature = BLSSignature.sign(keyPair, message1, domain);
    assertFalse(signature.checkSignature(keyPair.publicKeyAsBytes(), message2, domain));
  }

  @Test
  void succeedsWhenVerifyingDifferentPublicKeyFails() {
    BLSKeyPair keyPair1 = BLSKeyPair.random();
    BLSKeyPair keyPair2 = BLSKeyPair.random();
    while (keyPair1.equals(keyPair2)) {
      keyPair2 = BLSKeyPair.random();
    }
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    long domain = 42;
    BLSSignature signature = BLSSignature.sign(keyPair1, message, domain);
    assertFalse(signature.checkSignature(keyPair2.publicKeyAsBytes(), message, domain));
  }

  @Test
  void succeedsWhenSSZDecodeEncodeReturnsTheSameSignature() {
    BLSSignature signature1 = BLSSignature.random();
    BLSSignature signature2 = BLSSignature.fromBytes(signature1.toBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  @Disabled
  // This is not yet implemented
  void succeedsWhenSSZDecodeEncodeReturnsTheSameSignatureForTheEmptySignature() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.fromBytes(signature1.toBytes());
    assertEquals(signature1, signature2);
  }
}
