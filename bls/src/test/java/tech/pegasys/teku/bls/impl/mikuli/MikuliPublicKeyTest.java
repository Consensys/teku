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

package tech.pegasys.teku.bls.impl.mikuli;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.PublicKeyTest;

public class MikuliPublicKeyTest extends PublicKeyTest {

  @Override
  protected BLS12381 getBls() {
    return MikuliBLS12381.INSTANCE;
  }

  @Test
  void zeroSecretKeyGivesPointAtInfinity() {
    MikuliPublicKey pubKey = new MikuliSecretKey(new Scalar(new BIG(0))).derivePublicKey();
    assertTrue(pubKey.g1Point().ecpPoint().is_infinity());
  }

  @Test
  void succeedsWhenTwoInfinityPublicKeysAreEqual() {
    // Infinity keys are valid G1 points, so pass the equality test
    MikuliPublicKey publicKey1 = new MikuliPublicKey(new G1Point());
    MikuliPublicKey publicKey2 = new MikuliPublicKey(new G1Point());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void succeedsIfDeserializationOfInfinityPublicKeyIsCorrect() {
    MikuliPublicKey infinityPublicKey = new MikuliPublicKey(new G1Point());
    byte[] pointBytes = new byte[48];
    pointBytes[0] = (byte) 0xc0;
    Bytes infinityBytesSsz =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytes(Bytes.wrap(pointBytes));
            });
    MikuliPublicKey deserializedPublicKey = MikuliPublicKey.fromBytesCompressed(infinityBytesSsz);
    assertEquals(infinityPublicKey, deserializedPublicKey);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheInfinityPublicKey() {
    MikuliPublicKey publicKey1 = new MikuliPublicKey(new G1Point());
    MikuliPublicKey publicKey2 =
        MikuliPublicKey.fromBytesCompressed(publicKey1.toBytesCompressed());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void infinityPublicKeyIsValid() {
    MikuliPublicKey infinityG1 =
        MikuliPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x"
                    + "c0000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"));

    assertThatCode(infinityG1::forceValidation).doesNotThrowAnyException();
  }
}
