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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSecretKey;

class MikuliSecretKeyTest {
  private static final Bytes32 PRIVATE_KEY_32_BYTES =
      Bytes32.fromHexString("0x2CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460");

  @Test
  void keyCanBeCreatedWith32ByteValue() {
    assertThat(PRIVATE_KEY_32_BYTES.size()).isEqualTo(32);

    final BLSSecretKey secretKey = BLSSecretKey.fromBytes(PRIVATE_KEY_32_BYTES);
    // mikuli always represents the key as 48 bytes so compare against the key left padded with 0s
    assertThat(secretKey.getSecretKey().toBytes()).isEqualTo(Bytes48.leftPad(PRIVATE_KEY_32_BYTES));
  }
}
