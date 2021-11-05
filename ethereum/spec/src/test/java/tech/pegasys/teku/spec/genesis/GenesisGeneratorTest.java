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

package tech.pegasys.teku.spec.genesis;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

// Note that genesis generation is also covered by the initialization acceptance test
class GenesisGeneratorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final GenesisGenerator genesisGenerator =
      new GenesisGenerator(genesisSpec, spec.fork(UInt64.ZERO));

  private final List<DepositData> initialDepositData =
      new MockStartDepositGenerator(new DepositGenerator(spec, true))
          .createDeposits(VALIDATOR_KEYS);
  private final List<Deposit> initialDeposits =
      IntStream.range(0, initialDepositData.size())
          .mapToObj(
              index -> {
                final DepositData data = initialDepositData.get(index);
                return new DepositWithIndex(data, UInt64.valueOf(index));
              })
          .collect(toList());

  @Test
  void initializeBeaconStateFromEth1_shouldIgnoreInvalidSignedDeposits() {
    List<DepositWithIndex> deposits = dataStructureUtil.randomDeposits(3);
    DepositWithIndex deposit = deposits.get(1);
    DepositData depositData = deposit.getData();
    DepositWithIndex invalidSigDeposit =
        new DepositWithIndex(
            new DepositData(
                depositData.getPubkey(),
                depositData.getWithdrawal_credentials(),
                depositData.getAmount(),
                BLSSignature.empty()),
            deposit.getIndex());
    deposits.set(1, invalidSigDeposit);

    BeaconState state =
        spec.initializeBeaconStateFromEth1(Bytes32.ZERO, UInt64.ZERO, deposits, Optional.empty());
    assertEquals(2, state.getValidators().size());
    assertEquals(
        deposits.get(0).getData().getPubkey().toBytesCompressed(),
        state.getValidators().get(0).getPubkeyBytes());
    assertEquals(
        deposits.get(2).getData().getPubkey().toBytesCompressed(),
        state.getValidators().get(1).getPubkeyBytes());
  }

  @Test
  public void shouldGenerateSameGenesisAsSpecMethodForSingleDeposit() {
    final Bytes32 eth1BlockHash1 = dataStructureUtil.randomBytes32();
    final Bytes32 eth1BlockHash2 = dataStructureUtil.randomBytes32();

    final UInt64 genesisTime = UInt64.valueOf(982928293223232L);

    final BeaconState expectedState =
        spec.initializeBeaconStateFromEth1(
            eth1BlockHash2, genesisTime, initialDeposits, Optional.empty());

    genesisGenerator.updateCandidateState(
        eth1BlockHash1, genesisTime.minus(UInt64.ONE), initialDeposits.subList(0, 8));

    genesisGenerator.updateCandidateState(
        eth1BlockHash2, genesisTime, initialDeposits.subList(8, initialDeposits.size()));

    final BeaconState actualState = genesisGenerator.getGenesisState();
    assertThat(actualState).isEqualTo(expectedState);
    Assertions.<Integer>assertThat(spec.getActiveValidatorIndices(expectedState, GENESIS_EPOCH))
        .hasSize(VALIDATOR_KEYS.size());
    assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(VALIDATOR_KEYS.size());
  }

  @Test
  public void shouldIncrementallyAddValidators() {
    for (int i = 0; i < initialDeposits.size(); i++) {
      genesisGenerator.updateCandidateState(
          Bytes32.ZERO, UInt64.ZERO, Collections.singletonList(initialDeposits.get(i)));

      final BeaconState state = genesisGenerator.getGenesisState();
      Assertions.<Integer>assertThat(spec.getActiveValidatorIndices(state, GENESIS_EPOCH))
          .hasSize(i + 1);
      assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(i + 1);
    }
  }

  @Test
  public void shouldActivateToppedUpValidator() {
    MockStartDepositGenerator mockStartDepositGenerator =
        new MockStartDepositGenerator(new DepositGenerator(spec, true));
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
    Assertions.<Integer>assertThat(spec.getActiveValidatorIndices(state, GENESIS_EPOCH)).hasSize(1);
    assertThat(genesisGenerator.getActiveValidatorCount()).isEqualTo(1);
  }

  @Test
  public void shouldIgnoreInvalidDeposits() {
    List<Deposit> deposits = new ArrayList<>(initialDeposits);
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
    assertThat(validator.getPubkeyBytes()).isEqualTo(validData.getPubkey().toBytesCompressed());
    assertThat(genesisSpec.predicates().isActiveValidator(validator, GENESIS_EPOCH)).isTrue();
  }

  @Test
  public void shouldGenerateStateWithExecutionPayload() {
    final Spec spec = TestSpecFactory.createMinimalMerge();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final GenesisGenerator genesisGenerator =
        new GenesisGenerator(spec.getGenesisSpec(), spec.fork(UInt64.ZERO));

    genesisGenerator.updateCandidateState(
        dataStructureUtil.randomBytes32(),
        UInt64.valueOf(982928293223232L),
        initialDeposits.subList(0, 8));
    final ExecutionPayloadHeader payloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
    genesisGenerator.updateExecutionPayloadHeader(payloadHeader);
    final BeaconState actualState = genesisGenerator.getGenesisState();
    assertThat(actualState).isInstanceOf(BeaconStateMerge.class);
    assertThat(BeaconStateMerge.required(actualState).getLatestExecutionPayloadHeader())
        .isEqualTo(payloadHeader);
  }
}
