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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void succeedsIfSerializationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    assertEquals(
        "0x0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000",
        emptySignature.toBytes().toHexString());
  }

  @Test
  void succeedsIfDeserializationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    Bytes zeroBytes = Bytes.wrap(new byte[96]);
    Bytes emptyBytesSsz = SSZ.encode(writer -> writer.writeFixedBytes(zeroBytes));
    BLSSignature deserializedSignature = BLSSignature.fromBytes(emptyBytesSsz);
    assertEquals(emptySignature, deserializedSignature);
  }

  @Test
  void succeedsIfDeserializationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[95]);
    assertThrows(IllegalArgumentException.class, () -> BLSSignature.fromBytes(tooFewBytes));
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    BLSSignature signature = BLSSignature.random(9);
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalSignatures() {
    BLSSignature signature = BLSSignature.random(17);
    BLSSignature copyOfSignature = new BLSSignature(signature);
    assertEquals(signature, copyOfSignature);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    BLSSignature signature1 = BLSSignature.random(42);
    BLSSignature signature2 = BLSSignature.random(43);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSameSignature() {
    BLSSignature signature1 = BLSSignature.random(65);
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
