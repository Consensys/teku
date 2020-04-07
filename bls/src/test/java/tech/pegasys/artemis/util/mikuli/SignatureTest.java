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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.bls.BLSSignature;
import tech.pegasys.artemis.bls.mikuli.Signature;

class SignatureTest {

  public static final int HEX_CHARS_REQUIRED = 96 * 2;

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    Signature signature = Signature.random(42);
    assertEquals(signature, signature);
    assertEquals(signature.hashCode(), signature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalSignatures() {
    Signature signature = Signature.random(117);
    Signature copyOfSignature = new Signature(signature);
    assertEquals(signature, copyOfSignature);
    assertEquals(signature.hashCode(), copyOfSignature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    Signature signature1 = Signature.random(1);
    Signature signature2 = Signature.random(2);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForEmptySignatures() {
    assertEquals(BLSSignature.empty().getSignature(), BLSSignature.empty().getSignature());
    assertEquals(
        BLSSignature.empty().getSignature().hashCode(),
        BLSSignature.empty().getSignature().hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForInvalidSignatures() {
    final Bytes rawData = Bytes.fromHexString("1".repeat(HEX_CHARS_REQUIRED));
    final Signature signature1 = Signature.fromBytes(rawData);
    final Signature signature2 = Signature.fromBytes(rawData);
    assertEquals(signature1, signature2);
    assertEquals(signature1.hashCode(), signature2.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentInvalidSignatures() {
    final Signature signature1 =
        Signature.fromBytes(Bytes.fromHexString("1".repeat(HEX_CHARS_REQUIRED)));
    final Signature signature2 =
        Signature.fromBytes(Bytes.fromHexString("2".repeat(HEX_CHARS_REQUIRED)));
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenSerialisedSignaturesAre192BytesLong() {
    Signature signature = Signature.random(13);
    assertEquals(signature.toBytes().size(), 192);
  }

  @Test
  void succeedsWhenPassingEmptyListToAggregateSignaturesDoesNotThrowException() {
    assertDoesNotThrow(() -> Signature.aggregate(Collections.emptyList()));
  }

  @Test
  void roundtripEncodeDecode() {
    Signature signature = Signature.random(257);
    final Signature result = Signature.fromBytes(signature.toBytes());
    assertEquals(signature, result);
    assertEquals(signature.hashCode(), result.hashCode());
  }

  @Test
  void roundtripEncodeDecodeCompressed() {
    Signature signature = Signature.random(513);
    final Signature result = Signature.fromBytesCompressed(signature.toBytesCompressed());
    assertEquals(signature, result);
    assertEquals(signature.hashCode(), result.hashCode());
  }
}
