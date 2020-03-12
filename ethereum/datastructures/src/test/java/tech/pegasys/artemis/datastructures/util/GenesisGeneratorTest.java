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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateCache;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.TransitionCaches;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

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
                return new DepositWithIndex(data, UnsignedLong.valueOf(index));
              })
          .collect(toList());
  public static final UnsignedLong GENESIS_EPOCH = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);

  private int seed = 2489232;
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  @Test
  public void shouldGenerateSameGenesisAsSpecMethodForSingleDeposit() {
    final Bytes32 eth1BlockHash1 = DataStructureUtil.randomBytes32(seed++);
    final Bytes32 eth1BlockHash2 = DataStructureUtil.randomBytes32(seed++);

    final UnsignedLong genesisTime = UnsignedLong.valueOf(982928293223232L);

    final BeaconState expectedState =
        BeaconStateUtil.initialize_beacon_state_from_eth1(
            eth1BlockHash2, genesisTime, INITIAL_DEPOSITS);

    genesisGenerator.updateCandidateState(
        eth1BlockHash1, genesisTime.minus(UnsignedLong.ONE), INITIAL_DEPOSITS.subList(0, 8));

    genesisGenerator.updateCandidateState(
        eth1BlockHash2, genesisTime, INITIAL_DEPOSITS.subList(8, INITIAL_DEPOSITS.size()));

    final BeaconState actualState = genesisGenerator.getGenesisState();
    assertThat(actualState).isEqualTo(expectedState);
    assertThat(get_active_validator_indices(expectedState, GENESIS_EPOCH))
        .hasSize(VALIDATOR_KEYS.size());
  }

  @Test
  public void shouldIncrementallyAddValidators() {
    for (int i = 0; i < INITIAL_DEPOSITS.size(); i++) {
      genesisGenerator.updateCandidateState(
          Bytes32.ZERO, UnsignedLong.ZERO, Collections.singletonList(INITIAL_DEPOSITS.get(i)));

      final BeaconState state = genesisGenerator.getGenesisState();
      assertThat(get_active_validator_indices(state, GENESIS_EPOCH)).hasSize(i + 1);
    }
  }

  @Test
  public void shouldNotCacheActiveValidators() {
    // get_active_validator_indices caches the results based on the epoch. Since we keep adding
    // validators to the genesis epoch we must ensure they aren't cached.
    final Predicate<BeaconState> validityCriteria =
        candidate -> get_active_validator_indices(candidate, GENESIS_EPOCH).size() == 2;

    genesisGenerator.updateCandidateState(
        Bytes32.ZERO, UnsignedLong.ZERO, Collections.singletonList(INITIAL_DEPOSITS.get(0)));
    assertThat(genesisGenerator.getGenesisStateIfValid(validityCriteria)).isEmpty();

    // Now we should have two validators, not the 1 that would have been cached before.
    genesisGenerator.updateCandidateState(
        Bytes32.ZERO, UnsignedLong.ZERO, Collections.singletonList(INITIAL_DEPOSITS.get(1)));
    final Optional<MutableBeaconState> state =
        genesisGenerator.getGenesisStateIfValid(validityCriteria);
    assertThat(state).isNotEmpty();

    // And caching should be enabled on the final generated state.
    assertThat(BeaconStateCache.getTransitionCaches(state.get()))
        .isNotSameAs(TransitionCaches.getNoOp());
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

    genesisGenerator.updateCandidateState(Bytes32.ZERO, UnsignedLong.ZERO, deposits);
    final BeaconState state = genesisGenerator.getGenesisState();
    // All deposits were processed
    assertThat(state.getEth1_deposit_index()).isEqualTo(UnsignedLong.valueOf(deposits.size()));
    // But one didn't result in a new validator
    assertThat(state.getValidators()).hasSize(deposits.size() - 1);
    // And the validator with an invalid deposit should wind up at index 3, not 0 because their
    // first deposit was completely ignored
    final Validator validator = state.getValidators().get(expectedIndex);
    assertThat(validator.getPubkey()).isEqualTo(validData.getPubkey());
    assertThat(is_active_validator(validator, GENESIS_EPOCH)).isTrue();
  }

  @Test
  public void shouldReturnEmptyWhenValidityCriteriaAreNotMet() {
    assertThat(genesisGenerator.getGenesisStateIfValid(state -> false)).isEmpty();
  }

  @Test
  public void shouldReturnStateWhenValidityCriteriaAreMet() {
    assertThat(genesisGenerator.getGenesisStateIfValid(state -> true)).isNotEmpty();
  }
}
