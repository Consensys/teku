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

package tech.pegasys.teku.datastructures.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.interop.MockStartBeaconStateGenerator;
import tech.pegasys.teku.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class MockStartBeaconStateGeneratorTest {

  @Test
  public void shouldCreateInitialBeaconChainState() {
    final UInt64 genesisTime = UInt64.valueOf(498294294824924924L);
    final int validatorCount = 10;

    final List<BLSKeyPair> validatorKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);

    final List<DepositData> deposits =
        new MockStartDepositGenerator().createDeposits(validatorKeyPairs);

    final BeaconState initialBeaconState =
        new MockStartBeaconStateGenerator().createInitialBeaconState(genesisTime, deposits);

    assertEquals(validatorCount, initialBeaconState.getValidators().size());
    assertEquals(validatorCount, initialBeaconState.getEth1_data().getDeposit_count().longValue());

    final List<BLSPublicKey> actualValidatorPublicKeys =
        initialBeaconState.getValidators().stream()
            .map(Validator::getPubkey)
            .map(BLSPublicKey::fromBytesCompressed)
            .collect(Collectors.toList());
    final List<BLSPublicKey> expectedValidatorPublicKeys =
        validatorKeyPairs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList());
    assertEquals(expectedValidatorPublicKeys, actualValidatorPublicKeys);
  }
}
