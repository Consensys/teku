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

package tech.pegasys.artemis.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class BLSSignatureTest {

  Bytes48 c0 = Bytes48.random();
  Bytes48 c1 = Bytes48.random();

  BLSSignature signature = new BLSSignature(c0, c1);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BLSSignature testBLSSignature = signature;

    assertEquals(signature, testBLSSignature);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BLSSignature testBLSSignature = new BLSSignature(c0, c1);

    assertEquals(signature, testBLSSignature);
  }

  @Test
  void equalsReturnsFalseWhenC0IsDifferent() {
    BLSSignature testBLSSignature = new BLSSignature(c0.not(), c1);

    assertNotEquals(signature, testBLSSignature);
  }

  @Test
  void equalsReturnsFalseWhenC1IsDifferent() {
    BLSSignature testBLSSignature = new BLSSignature(c0, c1.not());

    assertNotEquals(signature, testBLSSignature);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszBLSSignatureBytes = signature.toBytes();
    assertEquals(signature, BLSSignature.fromBytes(sszBLSSignatureBytes));
  }
}
