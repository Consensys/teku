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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;

class BLSSignatureTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameEmptySignature() {
    BLSSignature signature = BLSSignature.empty();
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTwoEmptySignatures() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.empty();
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenCallingCheckSignatureOnEmptySignatureThrowsRuntimeException() {
    BLSSignature signature = BLSSignature.empty();
    assertThrows(
        RuntimeException.class,
        () ->
            signature.checkSignature(
                BLSPublicKey.random(),
                Bytes.wrap("Test".getBytes(UTF_8)),
                Bytes.wrap(new byte[8])));
  }

  @Test
  void succeedsIfSerialisationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    assertEquals(
        "0x0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000",
        emptySignature.toBytes().toHexString());
  }

  @Test
  void succeedsIfDeserialisationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    Bytes zeroBytes = Bytes.wrap(new byte[96]);
    Bytes emptyBytesSsz = SSZ.encode(writer -> writer.writeFixedBytes(zeroBytes));
    BLSSignature deserialisedSignature = BLSSignature.fromBytes(emptyBytesSsz);
    assertEquals(emptySignature, deserialisedSignature);
  }

  @Test
  void succeedsIfDeserialisationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[99]);
    assertThrows(IllegalArgumentException.class, () -> BLSSignature.fromBytes(tooFewBytes));
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
    BLSSignature signature1 = BLSSignature.random(42);
    BLSSignature signature2 = BLSSignature.random(43);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenAMessageSignsAndVerifies() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes domain = Bytes.random(8);
    BLSSignature signature = BLSSignature.sign(keyPair, message, domain);
    assertTrue(signature.checkSignature(keyPair.getPublicKey(), message, domain));
  }

  @Test
  void succeedsWhenVerifyingDifferentDomainFails() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes domain1 = Bytes.ofUnsignedLong(42L);
    Bytes domain2 = Bytes.ofUnsignedLong(43L);
    BLSSignature signature = BLSSignature.sign(keyPair, message, domain1);
    assertFalse(signature.checkSignature(keyPair.getPublicKey(), message, domain2));
  }

  @Test
  void succeedsWhenVerifyingDifferentMessageFails() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    Bytes domain = Bytes.ofUnsignedLong(42L);
    BLSSignature signature = BLSSignature.sign(keyPair, message1, domain);
    assertFalse(signature.checkSignature(keyPair.getPublicKey(), message2, domain));
  }

  @Test
  void succeedsWhenVerifyingDifferentPublicKeyFails() {
    BLSKeyPair keyPair1 = BLSKeyPair.random(1969);
    BLSKeyPair keyPair2 = BLSKeyPair.random(2019);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes domain = Bytes.ofUnsignedLong(42L);
    BLSSignature signature = BLSSignature.sign(keyPair1, message, domain);
    assertFalse(signature.checkSignature(keyPair2.getPublicKey(), message, domain));
  }

  @Test
  void succeedsWhenVerifyingKeyPairsAreSeededTheSame() {
    BLSKeyPair keyPair1 = BLSKeyPair.random(1);
    BLSKeyPair keyPair2 = BLSKeyPair.random(1);
    assertEquals(keyPair1.getPublicKey(), keyPair2.getPublicKey());
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes domain = Bytes.ofUnsignedLong(42L);
    BLSSignature signature1 = BLSSignature.sign(keyPair1, message, domain);
    BLSSignature signature2 = BLSSignature.sign(keyPair2, message, domain);
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSameSignature() {
    BLSSignature signature1 = BLSSignature.random();
    BLSSignature signature2 = BLSSignature.fromBytes(signature1.toBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheEmptySignature() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.fromBytes(signature1.toBytes());
    assertEquals(signature1, signature2);
  }
}
