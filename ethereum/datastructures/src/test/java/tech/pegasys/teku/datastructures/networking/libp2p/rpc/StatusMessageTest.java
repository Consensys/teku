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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

class StatusMessageTest {
  @Test
  public void shouldRoundTripViaSsz() {
    final StatusMessage message =
        new StatusMessage(
            Constants.GENESIS_FORK_VERSION,
            Bytes32.fromHexStringLenient("0x01"),
            UInt64.valueOf(2),
            Bytes32.fromHexStringLenient("0x03"),
            UInt64.valueOf(4));

    final Bytes data = SimpleOffsetSerializer.serialize(message);
    final StatusMessage result = SimpleOffsetSerializer.deserialize(data, StatusMessage.class);
    assertThat(result).isEqualToComparingFieldByField(message);
  }
}
