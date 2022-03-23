/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.eth1;

import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;

import org.junit.jupiter.api.Test;

class Eth1AddressTest {

  @Test
  void eth1Address_shouldRoundTrip() throws Exception {
    assertRoundTrip(
        Eth1Address.fromHexString("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6"),
        Eth1Address.getJsonTypeDefinition());
  }
}
