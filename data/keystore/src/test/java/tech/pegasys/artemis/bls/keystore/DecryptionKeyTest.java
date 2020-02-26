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

package tech.pegasys.artemis.bls.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

class DecryptionKeyTest {
  private static final Bytes SALT =
      Bytes.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes SCRYPT_DK =
      Bytes.fromHexString("0xBC21AF552ED055E3B3F35A39AFD8355903CA2770709B5E5B363647FA75234344");
  private static final Bytes PBKDF2_DK =
      Bytes.fromHexString("0x57E2285C828F4F6B95DEEC3BB6D9D90933042C63FC9BADE14EA280202A17142D");
  private static final String PASSWORD = "testpassword";

  @Test
  void sCryptDecryptionKeyGeneration() {
    final SCryptParam kdfParam = new SCryptParam(SALT);
    final Bytes decryptionKey = kdfParam.generateDecryptionKey(PASSWORD);
    assertThat(decryptionKey.size()).isEqualTo(32);
    assertThat(decryptionKey).isEqualTo(SCRYPT_DK);
  }

  @Test
  void pbkdf2DecryptionKeyGeneration() {
    final Pbkdf2Param kdfParam = new Pbkdf2Param(SALT);
    final Bytes decryptionKey = kdfParam.generateDecryptionKey(PASSWORD);
    assertThat(decryptionKey.size()).isEqualTo(32);
    assertThat(decryptionKey).isEqualTo(PBKDF2_DK);
  }
}
