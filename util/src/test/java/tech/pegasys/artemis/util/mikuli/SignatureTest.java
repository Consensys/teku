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

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class SignatureTest {

  private Signature signature = Signature.random();

  // TODO: Check all these still make sense
  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    Signature testBLSSignature = signature;

    assertEquals(signature, testBLSSignature);
  }

  @Test
  void equalsTrueWhenEncodedSignaturesAre192BytesLong() {
    assertEquals(signature.encode().size(), 192);
  }

  /* TODO: Fix these tests
    @Test
    void equalsReturnsTrueWhenObjectFieldsAreEqual() {
      Signature testBLSSignature = Signature.decode(Bytes.concatenate(c0, c1));

      assertEquals(signature, testBLSSignature);
    }

    @Test
    void equalsReturnsFalseWhenC0IsDifferent() {
      Signature testBLSSignature = Signature.decode(Bytes.concatenate(c0.not(), c1));

      assertNotEquals(signature, testBLSSignature);
    }

    @Test
    void equalsReturnsFalseWhenC1IsDifferent() {
      Signature testBLSSignature = Signature.decode(Bytes.concatenate(c0, c1.not()));

      assertNotEquals(signature, testBLSSignature);
    }

  */
  @Test
  void roundtripSSZ() {
    Bytes sszBLSSignatureBytes = signature.encode();
    assertEquals(signature, Signature.decode(sszBLSSignatureBytes));
  }
}
