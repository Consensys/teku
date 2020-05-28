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

package tech.pegasys.teku.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;

public class KeyFormatterTest {
  @Test
  public void shouldShowFirstSevenBytesOfPublicKey() {
    Bytes keyBytes =
        Bytes.fromHexString(
            "0xab10fc693d038b73d67279127501a05f0072cbb7147c68650ef6ac4e0a413e5cabd1f35c8711e1f7d9d885bbc3b8eddc");
    BLSPublicKey blsPublicKey = BLSPublicKey.fromBytes(keyBytes);
    assertThat(KeyFormatter.shortPublicKey(blsPublicKey)).isEqualTo("ab10fc6");
  }

  @Test
  public void shouldRaiseErrorForNullPublicKey() {
    assertThrows(IllegalArgumentException.class, () -> KeyFormatter.shortPublicKey(null));
  }
}
