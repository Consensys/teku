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

package tech.pegasys.artemis.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

class GenesisGeneratorTest {
  private int seed = 2489232;
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  @Test
  public void shouldGenerateSameGenesisAsSpecMethodForSingleDeposit() {
    final Bytes32 eth1BlockHash1 = DataStructureUtil.randomBytes32(seed++);
    final Bytes32 eth1BlockHash2 = DataStructureUtil.randomBytes32(seed++);
    final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(16);

    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(true)).createDeposits(validatorKeys);

    final UnsignedLong genesisTime = UnsignedLong.valueOf(982928293223232L);

    final List<DepositWithIndex> deposits = new ArrayList<>();
    for (int index = 0; index < initialDepositData.size(); index++) {
      final DepositData data = initialDepositData.get(index);
      DepositWithIndex deposit = new DepositWithIndex(data, UnsignedLong.valueOf(index));
      deposits.add(deposit);
    }

    final BeaconStateWithCache expectedState =
        BeaconStateUtil.initialize_beacon_state_from_eth1(eth1BlockHash2, genesisTime, deposits);

    genesisGenerator.addDepositsFromBlock(
        eth1BlockHash1, genesisTime.minus(UnsignedLong.ONE), deposits.subList(0, 8));

    genesisGenerator.addDepositsFromBlock(
        eth1BlockHash2, genesisTime, deposits.subList(8, deposits.size()));

    final BeaconStateWithCache actualState =
        genesisGenerator.getGenesisState(state -> true).orElseThrow();
    assertThat(actualState).isEqualTo(expectedState);
  }
}
