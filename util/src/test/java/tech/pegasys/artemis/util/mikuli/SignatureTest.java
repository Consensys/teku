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

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SignatureTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    Signature signature = Signature.random();
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalSignatures() {
    Signature signature = Signature.random();
    Signature copyOfSignature = new Signature(signature);
    assertEquals(signature, copyOfSignature);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    Signature signature1 = Signature.random();
    Signature signature2 = Signature.random();
    // Ensure that we have two different signatures, without assuming too much about .equals
    while (signature1.g2Point().ecp2Point().equals(signature2.g2Point().ecp2Point())) {
      signature2 = Signature.random();
    }
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenSerialisedSignaturesAre192BytesLong() {
    Signature signature = Signature.random();
    assertEquals(signature.toBytes().size(), 192);
  }

  @Test
  void succeedsWhenPassingEmptyListToAggregateSignaturesDoesNotThrowException() {
    assertDoesNotThrow(() -> Signature.aggregate(Arrays.asList()));
  }

  @Test
  void roundtripEncodeDecode() {
    Signature signature = Signature.random();
    assertEquals(signature, Signature.fromBytes(signature.toBytes()));
  }

  @Test
  void roundtripEncodeDecodeCompressed() {
    Signature signature = Signature.random();
    assertEquals(signature, Signature.fromBytesCompressed(signature.toBytesCompressed()));
  }
}
