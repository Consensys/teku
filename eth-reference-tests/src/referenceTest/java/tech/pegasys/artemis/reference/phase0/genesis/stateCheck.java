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

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

@ExtendWith(BouncyCastleExtension.class)
public class stateCheck extends TestSuite {

  private static int numValidators = 16;
  private static long genesisTime = 1567777777L;

  @Disabled
  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"genesisGenericInitializationSetup"})
  void genesisInitialization(BeaconState state) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numValidators);
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator().createDeposits(validatorKeys);
    BeaconStateWithCache newBeaconState =
        new MockStartBeaconStateGenerator()
            .createInitialBeaconState(UnsignedLong.valueOf(genesisTime), initialDepositData);
    Assertions.assertEquals(state, newBeaconState);
  }

  @MustBeClosed
  static Stream<Arguments> genesisGenericInitializationSetup() throws Exception {
    Path configPath = Paths.get("minimal", "phase0");
    Path path = Paths.get("/minimal/phase0/genesis/clientStateCheck");
    return genesisInitializationCheck(path, configPath, numValidators, genesisTime);
  }
}
