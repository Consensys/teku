/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.mikuli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.SignatureTest;

public class MikuliSignatureTest extends SignatureTest {
  public static final int HEX_CHARS_REQUIRED = 96 * 2;

  @Override
  protected BLS12381 getBls() {
    return MikuliBLS12381.INSTANCE;
  }

  @Test
  void succeedsWhenSerializedSignaturesAre192BytesLong() {
    MikuliSignature signature = MikuliSignature.random(13);
    assertEquals(signature.toBytesUncompressed().size(), 192);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForInvalidSignatures() {
    final Bytes rawData = Bytes.fromHexString("1".repeat(HEX_CHARS_REQUIRED));
    final MikuliSignature signature1 = MikuliSignature.fromBytes(rawData);
    final MikuliSignature signature2 = MikuliSignature.fromBytes(rawData);
    assertEquals(signature1, signature2);
    assertEquals(signature1.hashCode(), signature2.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentInvalidSignatures() {
    final MikuliSignature signature1 =
        MikuliSignature.fromBytes(Bytes.fromHexString("1".repeat(HEX_CHARS_REQUIRED)));
    final MikuliSignature signature2 =
        MikuliSignature.fromBytes(Bytes.fromHexString("2".repeat(HEX_CHARS_REQUIRED)));
    assertNotEquals(signature1, signature2);
  }
}
