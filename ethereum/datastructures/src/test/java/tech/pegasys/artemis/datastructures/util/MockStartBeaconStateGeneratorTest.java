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

package tech.pegasys.artemis.datastructures.util;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

class MockStartBeaconStateGeneratorTest {
  @Test
  public void shouldCreateInitialBeaconChainState() {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(498294294824924924L);
    final int validatorCount = 10;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount - 1);

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    final BeaconStateWithCache initialBeaconState =
        new MockStartBeaconStateGenerator().createInitialBeaconState(genesisTime, deposits);

    // TODO: Worst test ever... Need a reference genesis state.
    assertNotNull(initialBeaconState);
  }
}
