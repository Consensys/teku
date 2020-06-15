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

package tech.pegasys.teku.bls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BLSSecretKeyTest {
  private static final Bytes PRIVATE_KEY_32_BYTES =
      Bytes.fromHexString("0x2CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460");
  private static final Bytes PRIVATE_KEY_48_BYTES =
      Bytes.fromHexString(
          "0x000000000000000000000000000000002CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460");

  @Test
  void keyCanBeCreatedWith32ByteValue() {
    assertThat(PRIVATE_KEY_32_BYTES.size()).isEqualTo(32);

    final BLSSecretKey secretKey = BLSSecretKey.fromBytes(PRIVATE_KEY_32_BYTES);
    // mikuli always represents the key as 48 bytes so compare against the key left padded with 0s
    assertThat(secretKey.getSecretKey().toBytes()).isEqualTo(Bytes48.leftPad(PRIVATE_KEY_32_BYTES));
  }

  @Test
  void keyCanBeCreatedWith48ByteValue() {
    assertThat(PRIVATE_KEY_48_BYTES.size()).isEqualTo(48);

    final BLSSecretKey secretKey = BLSSecretKey.fromBytes(PRIVATE_KEY_48_BYTES);
    assertThat(secretKey.getSecretKey().toBytes()).isEqualTo(PRIVATE_KEY_48_BYTES);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 30, 31, 33, 47, 49})
  void keyCannotBeSizeOtherThan32Or48Bytes(int size) {
    final Bytes bytes = Bytes.wrap(new byte[size]);
    assertThat(bytes.size()).isEqualTo(size);
    assertThatThrownBy(() -> BLSSecretKey.fromBytes(bytes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected 32 or 48 bytes but received " + size + ".");
  }

  @Test
  void toBytes_trimsLeadingZerosFrom48BytesKey() {
    final BLSSecretKey secretKey = BLSSecretKey.fromBytes(PRIVATE_KEY_48_BYTES);
    assertThat(secretKey.toBytes()).isEqualTo(PRIVATE_KEY_32_BYTES);
  }

  @Test
  void toBytes_returns48BytesIfPaddingIsNotAllZero() {
    // Lots of leading zeros but that first 1 is in the 16 bytes of padding.
    final Bytes keyBytes =
        Bytes.fromHexString(
            "0x000000000000000000000000000000012CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460");
    final BLSSecretKey key = BLSSecretKey.fromBytes(keyBytes);
    assertThat(key.toBytes()).isEqualTo(keyBytes);
  }
}
