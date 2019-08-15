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

package tech.pegasys.artemis.validator.coordinator;

import static org.assertj.core.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

class MockStartValidatorKeyPairFactoryTest {
  private static final String[] EXPECTED_PRIVATE_KEYS = {
    "0x000000000000000000000000000000000066687AADF862BD776C8FC18B8E9F8E20089714856EE233B3902A591D0D5F29",
    "0x000000000000000000000000000000000001D0FABD251FCBBE2B93B4B927B26AD2A1A99077152E45DED1E678AFA45DBE",
    "0x00000000000000000000000000000000005778F985DB754C6628691F56FADAE50C65FDDBE8EB2E93039633FEFA05D45E",
    "0x000000000000000000000000000000000091D3827F052F5A4B44D5FE2BED657C752247365D94F80A33CB09C1436A16B1",
    "0x00000000000000000000000000000000004B78063B9C224DA311BD1D3FB969BBA19E7E91EE07B506F9C4C43882891556",
    "0x0000000000000000000000000000000000AAE761377F3B4F1F07D982783B902314B61A9CBE6CCFDFA96559039F07E332",
    "0x0000000000000000000000000000000000938834D6C6369917101C182C58E7834AA8763FEE2D35F7E06559075288C623",
    "0x0000000000000000000000000000000000F5411EC7E51E46159C654BDBDF3CC20785A217B87384810ED2E541DC001694",
    "0x0000000000000000000000000000000000067DC6A810183E9069F63C2020B692C122C8D58263ED7F5C0E531504DC3B6E",
    "0x000000000000000000000000000000000034EC81DBDAF9148567DC4254F93852D34F96E78DDD4BD04C1C8A1641A0883B",
    "0x0000000000000000000000000000000000177149DF55F3A2824842E5DF7CEE51BE33D65D79926A233B7C12E37FEADB62"
  };

  private final MockStartValidatorKeyPairFactory factory = new MockStartValidatorKeyPairFactory();

  @Test
  public void shouldGenerateValidKeys() {
    final List<BLSKeyPair> keyPairs = factory.generateKeyPairs(0, 10);
    final List<String> actualPrivateKeys =
        keyPairs.stream()
            .map(keyPair -> keyPair.getSecretKey().getSecretKey().toBytes().toHexString())
            .collect(Collectors.toList());

    assertEquals(asList(EXPECTED_PRIVATE_KEYS), actualPrivateKeys);
  }
}
