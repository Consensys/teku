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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import org.junit.jupiter.api.Test;

class BLSPublicKeyTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameEmptyPublicKey() {
    BLSPublicKey publicKey = BLSPublicKey.empty();
    assertEquals(publicKey, publicKey);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTwoEmptyPublicKeys() {
    BLSPublicKey publicKey1 = BLSPublicKey.empty();
    BLSPublicKey publicKey2 = BLSPublicKey.empty();
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void succeedsIfSerialisationOfEmptyPublicKeyIsCorrect() {
    BLSPublicKey emptyPublicKey = BLSPublicKey.empty();
    assertTrue(emptyPublicKey.isEmpty());
    // SSZ prepends the length as four little-endian bytes
    assertEquals(
        "0x30000000"
            + "000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000",
        emptyPublicKey.toBytes().toHexString());
  }

  @Test
  void succeedsIfDeserialisationOfEmptyPublicKeyIsCorrect() {
    BLSPublicKey emptyPublicKey = BLSPublicKey.empty();
    assertTrue(emptyPublicKey.isEmpty());
    Bytes zeroBytes = Bytes.wrap(new byte[48]);
    Bytes emptyBytesSsz = SSZ.encodeBytes(zeroBytes);
    BLSPublicKey deserialisedPublicKey = BLSPublicKey.fromBytes(emptyBytesSsz);
    assertEquals(emptyPublicKey, deserialisedPublicKey);
  }

  @Test
  void succeedsIfDeserialisationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[51]);
    assertThrows(IllegalArgumentException.class, () -> BLSPublicKey.fromBytes(tooFewBytes));
  }

  @Test
  void succeedsIfValidPublicKeyIsNotEmpty() {
    BLSPublicKey publicKey = BLSPublicKey.random();
    assertTrue(!publicKey.isEmpty());
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePublicKey() {
    BLSPublicKey publicKey = BLSPublicKey.random();
    assertEquals(publicKey, publicKey);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForIdenticalPublicKeys() {
    BLSPublicKey publicKey = BLSPublicKey.random();
    BLSPublicKey copyOfPublicKey = new BLSPublicKey(publicKey.getPublicKey());
    assertEquals(publicKey, copyOfPublicKey);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPublicKeys() {
    BLSPublicKey publicKey1 = BLSPublicKey.random();
    BLSPublicKey publicKey2 = BLSPublicKey.random();
    // Ensure that we have two different publicKeys, without assuming too much about .equals
    while (publicKey1.getPublicKey().equals(publicKey2.getPublicKey())) {
      publicKey2 = BLSPublicKey.random();
    }
    assertNotEquals(publicKey1, publicKey2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSamePublicKey() {
    BLSPublicKey publicKey1 = BLSPublicKey.random();
    BLSPublicKey publicKey2 = BLSPublicKey.fromBytes(publicKey1.toBytes());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheEmptyPublicKey() {
    BLSPublicKey publicKey1 = BLSPublicKey.empty();
    BLSPublicKey publicKey2 = BLSPublicKey.fromBytes(publicKey1.toBytes());
    assertEquals(publicKey1, publicKey2);
  }
}
