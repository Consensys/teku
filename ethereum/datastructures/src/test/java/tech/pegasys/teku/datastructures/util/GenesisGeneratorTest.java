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

package tech.pegasys.teku.datastructures.util;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_active_validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

// Note that genesis generation is also covered by the initialization acceptance test
class GenesisGeneratorTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);
  private static final List<DepositData> INITIAL_DEPOSIT_DATA =
      new MockStartDepositGenerator(new DepositGenerator(true)).createDeposits(VALIDATOR_KEYS);
  private static final List<Deposit> INITIAL_DEPOSITS =
      IntStream.range(0, INITIAL_DEPOSIT_DATA.size())
          .mapToObj(
              index -> {
                final DepositData data = INITIAL_DEPOSIT_DATA.get(index);
                return new DepositWithIndex(data, UInt64.valueOf(index));
              })
          .collect(toList());
  public static final UInt64 GENESIS_EPOCH = UInt64.valueOf(Constants.GENESIS_EPOCH);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  @Test
  public void shouldGenerateSameGenesisAsSpecMethodForSingleDeposit() {
    final Bytes32 eth1BlockHash1 = dataStructureUtil.randomBytes32();
    final Bytes32 eth1BlockHash2 = dataStructureUtil.randomBytes32();

    final UInt64 genesisTime = UInt64.valueOf(982928293223232L);

    final BeaconState expectedState =
        BeaconStateUtil.initialize_beacon_state_from_eth1(
            eth1BlockHash2, genesisTime, INITIAL_DEPOSITS);

    genesisGenerator.updateCandidateState(
        eth1BlockHash1, genesisTime.minus(UInt64.ONE), INITIAL_DEPOSITS.subList(0, 8));

    genesisGenerator.updateCandidateState(
        eth1BlockHash2, genesisTime, INITIAL_DEPOSITS.subList(8, INITIAL_DEPOSITS.size()));

    final BeaconState actualState = genesisGenerator.getGenesisState();
    assertThat(actualState).isEqualTo(expectedState);
    assertThat(get_active_validator_indices(expectedState, GENESIS_EPOCH))
        .hasSize(VALIDATOR_KEYS.size());
    assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(VALIDATOR_KEYS.size());
  }

  @Test
  public void shouldIncrementallyAddValidators() {
    for (int i = 0; i < INITIAL_DEPOSITS.size(); i++) {
      genesisGenerator.updateCandidateState(
          Bytes32.ZERO, UInt64.ZERO, Collections.singletonList(INITIAL_DEPOSITS.get(i)));

      final BeaconState state = genesisGenerator.getGenesisState();
      assertThat(get_active_validator_indices(state, GENESIS_EPOCH)).hasSize(i + 1);
      assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(i + 1);
    }
  }

  @Test
  public void shouldActivateToppedUpValidator() {
    MockStartDepositGenerator mockStartDepositGenerator =
        new MockStartDepositGenerator(new DepositGenerator(true));
    DepositData PARTIAL_DEPOSIT_DATA =
        mockStartDepositGenerator
            .createDeposits(VALIDATOR_KEYS.subList(0, 1), UInt64.valueOf(1000000000L))
            .get(0);

    DepositData TOP_UP_DEPOSIT_DATA =
        mockStartDepositGenerator
            .createDeposits(VALIDATOR_KEYS.subList(0, 1), UInt64.valueOf(31000000000L))
            .get(0);

    List<DepositData> INITIAL_DEPOSIT_DATA = List.of(PARTIAL_DEPOSIT_DATA, TOP_UP_DEPOSIT_DATA);

    List<Deposit> INITIAL_DEPOSITS =
        IntStream.range(0, INITIAL_DEPOSIT_DATA.size())
            .mapToObj(
                index -> {
                  final DepositData data = INITIAL_DEPOSIT_DATA.get(index);
                  return new DepositWithIndex(data, UInt64.valueOf(index));
                })
            .collect(toList());

    genesisGenerator.updateCandidateState(Bytes32.ZERO, UInt64.ZERO, INITIAL_DEPOSITS);

    final BeaconState state = genesisGenerator.getGenesisState();
    assertThat(get_active_validator_indices(state, GENESIS_EPOCH)).hasSize(1);
    assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(1);
  }

  @Test
  public void shouldIgnoreInvalidDeposits() {
    List<Deposit> deposits = new ArrayList<>(INITIAL_DEPOSITS);
    // Add an invalid deposit at the start with the same key as a later, valid deposit
    final int expectedIndex = 3;
    final DepositData validData = deposits.get(expectedIndex).getData();
    final DepositData invalidData =
        new DepositData(
            validData.getPubkey(),
            validData.getWithdrawal_credentials(),
            validData.getAmount(),
            BLSSignature.empty());
    deposits.add(0, new Deposit(invalidData));

    genesisGenerator.updateCandidateState(Bytes32.ZERO, UInt64.ZERO, deposits);
    final BeaconState state = genesisGenerator.getGenesisState();
    // All deposits were processed
    assertThat(state.getEth1_deposit_index()).isEqualTo(UInt64.valueOf(deposits.size()));
    // But one didn't result in a new validator
    assertThat(state.getValidators()).hasSize(deposits.size() - 1);
    assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(deposits.size() - 1);
    // And the validator with an invalid deposit should wind up at index 3, not 0 because their
    // first deposit was completely ignored
    final Validator validator = state.getValidators().get(expectedIndex);
    assertThat(validator.getPubkey()).isEqualTo(validData.getPubkey().toBytesCompressed());
    assertThat(is_active_validator(validator, GENESIS_EPOCH)).isTrue();
  }
}
