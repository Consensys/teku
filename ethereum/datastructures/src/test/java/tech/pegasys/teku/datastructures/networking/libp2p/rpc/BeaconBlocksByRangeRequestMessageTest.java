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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.SszTestUtils;

class BeaconBlocksByRangeRequestMessageTest {

  @Test
  public void shouldRoundTripViaSsz() {
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(
            UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(4));
    final Bytes data = request.sszSerialize();
    final BeaconBlocksByRangeRequestMessage result =
        BeaconBlocksByRangeRequestMessage.SSZ_SCHEMA.sszDeserialize(data);

    assertThat(SszTestUtils.equalsByGetters(result, request)).isTrue();
    assertThat(result).isEqualTo(request);
  }
}
