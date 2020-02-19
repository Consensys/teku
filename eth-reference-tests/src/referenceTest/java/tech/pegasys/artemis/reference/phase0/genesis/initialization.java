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

package tech.pegasys.artemis.reference.phase0.genesis;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class initialization extends TestSuite {

  @ParameterizedTest(name = "{index}.{3} root of Merkleizable")
  @MethodSource({"genesisGenericInitializationSetup"})
  void genesisInitialization(
      BeaconStateImpl state,
      UnsignedLong eth1_timestamp,
      Bytes32 eth1_block_hash,
      String testName,
      List<? extends Deposit> deposits) {
    BeaconState beaconState =
        initialize_beacon_state_from_eth1(eth1_block_hash, eth1_timestamp, deposits);
    Assertions.assertEquals(state, beaconState);
  }

  @MustBeClosed
  static Stream<Arguments> genesisGenericInitializationSetup() throws Exception {
    Path configPath = Paths.get("minimal", "phase0");
    Path path =
        Paths.get(
            "/minimal/phase0/genesis/initialization/pyspec_tests/initialize_beacon_state_from_eth1");
    return genesisInitializationSetup(path, configPath);
  }
}
