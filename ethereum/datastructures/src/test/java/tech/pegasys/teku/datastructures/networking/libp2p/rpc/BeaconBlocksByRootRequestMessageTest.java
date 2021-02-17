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

import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class BeaconBlocksByRootRequestMessageTest {

  @Test
  public void shouldRoundTripViaSsz() {
    final BeaconBlocksByRootRequestMessage request =
        new BeaconBlocksByRootRequestMessage(
            List.of(
                Bytes32.fromHexString(
                    "0x1111111111111111111111111111111111111111111111111111111111111111"),
                Bytes32.fromHexString(
                    "0x2222222222222222222222222222222222222222222222222222222222222222")));
    final Bytes data = request.sszSerialize();
    final BeaconBlocksByRootRequestMessage result =
        BeaconBlocksByRootRequestMessage.SSZ_SCHEMA.sszDeserialize(data);
    assertThatSszData(result).isEqualByAllMeansTo(request);
  }
}
