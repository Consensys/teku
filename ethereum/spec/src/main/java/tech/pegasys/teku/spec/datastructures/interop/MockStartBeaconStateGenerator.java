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

package tech.pegasys.teku.spec.datastructures.interop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class MockStartBeaconStateGenerator {
  private static final Bytes32 BLOCK_HASH;

  private final Spec spec;

  static {
    final byte[] eth1BlockHashBytes = new byte[32];
    Arrays.fill(eth1BlockHashBytes, (byte) 0x42);
    BLOCK_HASH = Bytes32.wrap(eth1BlockHashBytes);
  }

  public MockStartBeaconStateGenerator(final Spec spec) {
    this.spec = spec;
  }

  public BeaconState createInitialBeaconState(
      final UInt64 genesisTime,
      final List<DepositData> initialDepositData,
      final Optional<ExecutionPayloadHeader> payloadHeader) {
    final List<DepositWithIndex> deposits = new ArrayList<>();
    for (int index = 0; index < initialDepositData.size(); index++) {
      final DepositData data = initialDepositData.get(index);
      DepositWithIndex deposit = new DepositWithIndex(data, UInt64.valueOf(index));
      deposits.add(deposit);
    }
    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(BLOCK_HASH, genesisTime, deposits, payloadHeader);
    return initialState.updated(state -> state.setGenesis_time(genesisTime));
  }
}
