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

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

public final class InteropStartupUtil {

  public static BeaconState createMockedStartInitialBeaconState(
      final Spec spec, final long genesisTime, final int numValidators) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numValidators);
    return createMockedStartInitialBeaconState(spec, genesisTime, validatorKeys, true);
  }

  public static BeaconState createMockedStartInitialBeaconState(
      final Spec spec,
      final Bytes32 eth1BlockHash,
      final long genesisTime,
      List<BLSKeyPair> validatorKeys) {
    return createMockedStartInitialBeaconState(
        spec, eth1BlockHash, genesisTime, validatorKeys, true);
  }

  public static BeaconState createMockedStartInitialBeaconState(
      final Spec spec,
      final long genesisTime,
      List<BLSKeyPair> validatorKeys,
      boolean signDeposits) {
    return createMockedStartInitialBeaconState(
        spec,
        MockStartBeaconStateGenerator.INTEROP_ETH1_BLOCK_HASH,
        genesisTime,
        validatorKeys,
        signDeposits);
  }

  public static BeaconState createMockedStartInitialBeaconState(
      final Spec spec,
      final Bytes32 eth1BlockHash,
      final long genesisTime,
      List<BLSKeyPair> validatorKeys,
      boolean signDeposits) {
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(signDeposits, spec))
            .createDeposits(validatorKeys);
    return new MockStartBeaconStateGenerator(spec)
        .createInitialBeaconState(eth1BlockHash, UInt64.valueOf(genesisTime), initialDepositData);
  }
}
