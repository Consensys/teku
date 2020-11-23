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

package tech.pegasys.teku.api.response.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

class HeadEventTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  void shouldParseFromEventWithoutDependentRoots() throws Exception {
    final String json =
        "{\"slot\":\"4666673844721362956\",\"block\":\"0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef\",\"state\":\"0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e\",\"epoch_transition\":true}\n";
    final HeadEvent result = jsonProvider.jsonToObject(json, HeadEvent.class);
    assertThat(result)
        .isEqualTo(
            new HeadEvent(
                UInt64.valueOf(4666673844721362956L),
                Bytes32.fromHexString(
                    "0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef"),
                Bytes32.fromHexString(
                    "0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e"),
                true,
                null,
                null));
  }

  @Test
  void shouldRoundTripJsonParsing() throws Exception {
    final HeadEvent input =
        new HeadEvent(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            true,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
    final String json = jsonProvider.objectToJSON(input);
    final HeadEvent result = jsonProvider.jsonToObject(json, HeadEvent.class);
    assertThat(result).isEqualTo(input);
  }
}
