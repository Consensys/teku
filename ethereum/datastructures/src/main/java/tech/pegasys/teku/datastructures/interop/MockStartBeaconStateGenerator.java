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

package tech.pegasys.teku.datastructures.interop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MockStartBeaconStateGenerator {

  private static final Bytes32 BLOCK_HASH;

  static {
    final byte[] eth1BlockHashBytes = new byte[32];
    Arrays.fill(eth1BlockHashBytes, (byte) 0x42);
    BLOCK_HASH = Bytes32.wrap(eth1BlockHashBytes);
  }

  public BeaconState createInitialBeaconState(
      final UInt64 genesisTime, final List<DepositData> initialDepositData) {
    final List<DepositWithIndex> deposits = new ArrayList<>();
    for (int index = 0; index < initialDepositData.size(); index++) {
      final DepositData data = initialDepositData.get(index);
      DepositWithIndex deposit = new DepositWithIndex(data, UInt64.valueOf(index));
      deposits.add(deposit);
    }
    final BeaconState initialState =
        BeaconStateUtil.initialize_beacon_state_from_eth1(BLOCK_HASH, genesisTime, deposits);
    return initialState.updated(state -> state.setGenesis_time(genesisTime));
  }
}
