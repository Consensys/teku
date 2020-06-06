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

package tech.pegasys.teku.bls;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class BLSTest {

  @Test
  void succeedsWhenWeCanSignAndVerify() {
    BLSKeyPair keyPair = BLSKeyPair.random(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature signature = BLS.sign(keyPair.getSecretKey(), message);
    assertTrue(BLS.verify(keyPair.getPublicKey(), message, signature));
  }

  @Test
  void succeedsWhenCallingVerifyWithEmptySignatureReturnsFalse() {
    assertFalse(
        BLS.verify(
            BLSPublicKey.random(17), Bytes.wrap("Test".getBytes(UTF_8)), BLSSignature.empty()));
  }

  @Test
  void succeedsWhenAggregatingASingleSignatureReturnsTheSameSignature() {
    BLSSignature signature = BLSSignature.random(1);
    assertEquals(signature, BLS.aggregate(Collections.singletonList(signature)));
  }

  @Test
  void succeedsWhenPassingEmptySignatureToAggregateSignaturesThrowsIllegalArgumentException() {
    BLSSignature signature1 = BLSSignature.random(1);
    BLSSignature signature2 = BLSSignature.empty();
    BLSSignature signature3 = BLSSignature.random(3);
    assertThrows(
        IllegalArgumentException.class,
        () -> BLS.aggregate(Arrays.asList(signature1, signature2, signature3)));
  }

  @Test
  void succeedsWhenCorrectlySigningAndVerifyingAggregateSignaturesReturnsTrue() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSKeyPair keyPair1 = BLSKeyPair.random(1);
    BLSKeyPair keyPair2 = BLSKeyPair.random(2);
    BLSKeyPair keyPair3 = BLSKeyPair.random(3);

    List<BLSPublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<BLSSignature> signatures =
        Arrays.asList(
            BLS.sign(keyPair1.getSecretKey(), message),
            BLS.sign(keyPair2.getSecretKey(), message),
            BLS.sign(keyPair3.getSecretKey(), message));
    BLSSignature aggregatedSignature = BLS.aggregate(signatures);

    assertTrue(BLS.fastAggregateVerify(publicKeys, message, aggregatedSignature));
  }

  static final BLSPublicKey infinityG1 =
      BLSPublicKey.fromBytesCompressed(
          Bytes.fromHexString(
              "0x"
                  + "c0000000000000000000000000000000"
                  + "00000000000000000000000000000000"
                  + "00000000000000000000000000000000"));
  static final BLSSignature infinityG2 =
      BLSSignature.fromBytes(
          Bytes.fromHexString(
              "0x"
                  + "c000000000000000000000000000000000000000000000000000000000000000"
                  + "0000000000000000000000000000000000000000000000000000000000000000"
                  + "0000000000000000000000000000000000000000000000000000000000000000"));
  static final BLSSecretKey zeroSK = BLSSecretKey.fromBytes(Bytes32.ZERO);

  @Test
  void succeedsWhenPubkeyAndSignatureBothTheIdentityIsOK() {
    // Any message should verify
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    assertTrue(BLS.verify(infinityG1, message, infinityG2));
  }

  @Test
  void succeedsWhenZeroSecretKeyGivesInfinitePublicKey() {
    assertEquals(infinityG1, new BLSPublicKey(zeroSK));
  }

  @Test
  void succeedsWhenInfinitePublicKeyGivesInfiniteSignature() {
    // Any message should result in the signature at infinity
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    assertEquals(infinityG2, BLS.sign(zeroSK, message));
  }
}
