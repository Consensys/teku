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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.statetransition.BeaconChainUtil.initializeStorage;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.VoluntaryExitGenerator;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DepositGenerator;
import tech.pegasys.teku.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.teku.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.teku.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class VoluntaryExitValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private final VoluntaryExitGenerator voluntaryExitGenerator =
      new VoluntaryExitGenerator(VALIDATOR_KEYS);

  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;
  private VoluntaryExitValidator voluntaryExitValidator;

  @BeforeAll
  static void beforeAll() {
    Constants.SLOTS_PER_EPOCH = 2;
    Constants.EPOCHS_PER_ETH1_VOTING_PERIOD = 1;
    Constants.PERSISTENT_COMMITTEE_PERIOD = 2;
  }

  @AfterAll
  static void afterAll() {
    Constants.setConstants("minimal");
  }

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
    beaconChainUtil = BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);
    voluntaryExitValidator = new VoluntaryExitValidator(recentChainData);
  }

  @Test
  public void shouldReturnValidForValidVoluntaryExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldReturnInvalidForExitsAfterTheFirstForValidator() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit1 =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);

    SignedVoluntaryExit exit2 =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);

    SignedVoluntaryExit exit3 =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);

    assertThat(voluntaryExitValidator.validate(exit1)).isEqualTo(ACCEPT);
    assertThat(voluntaryExitValidator.validate(exit2)).isEqualTo(IGNORE);
    assertThat(voluntaryExitValidator.validate(exit3)).isEqualTo(IGNORE);
  }

  @Test
  public void shouldReturnInvalidForExitWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit1 =
        voluntaryExitGenerator.withInvalidSignature(
            recentChainData.getBestState().orElseThrow(), 3);
    assertThat(voluntaryExitValidator.validate(exit1)).isEqualTo(REJECT);
  }

  @Test
  public void shouldReturnInvalidForExitOfInactiveValidator() throws Exception {
    final DepositGenerator depositGenerator = new DepositGenerator(true);
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(depositGenerator)
            .createDeposits(VALIDATOR_KEYS.subList(0, 10));
    // Add an inactive validator (they haven't deposited enough to become a validator)
    final BLSKeyPair inactiveValidatorKeyPair = VALIDATOR_KEYS.get(10);
    initialDepositData.add(
        depositGenerator.createDepositData(
            inactiveValidatorKeyPair, UnsignedLong.ONE, inactiveValidatorKeyPair.getPublicKey()));
    final BeaconState genesisState =
        new MockStartBeaconStateGenerator()
            .createInitialBeaconState(UnsignedLong.ZERO, initialDepositData);
    recentChainData.initializeFromGenesis(genesisState);
    SignedVoluntaryExit exit =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 10, false);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(REJECT);
  }

  @Test
  public void shouldReturnInvalidForExitWithInvalidValidatorIndex() throws Exception {
    initializeStorage(recentChainData, VALIDATOR_KEYS.subList(0, 10));
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 20, false);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(REJECT);
  }

  @Test
  public void shouldReturnInvalidForValidatorWithInitiatedExit() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit1 =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);

    beaconChainUtil.createAndImportBlockAtSlotWithExits(UnsignedLong.valueOf(7), List.of(exit1));

    SignedVoluntaryExit exit2 =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 3);
    assertThat(voluntaryExitValidator.validate(exit2)).isEqualTo(REJECT);
  }

  @Test
  public void shouldReturnInvalidForExitThatHasFutureEpoch() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6); // epoch at 3

    // Sanity check
    assertThat(compute_epoch_at_slot(recentChainData.getBestState().orElseThrow().getSlot()))
        .isEqualTo(UnsignedLong.valueOf(3));

    SignedVoluntaryExit exit =
        voluntaryExitGenerator.withEpoch(recentChainData.getBestState().orElseThrow(), 4, 3);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(REJECT);
  }

  @Test
  public void shouldReturnInvalidForValidatorThatHasntBeenActiveLongEnough() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(
        1); // epoch at 0, and persistent committee period is 2 epochs

    // Sanity check
    assertThat(compute_epoch_at_slot(recentChainData.getBestState().orElseThrow().getSlot()))
        .isEqualTo(UnsignedLong.valueOf(0));

    SignedVoluntaryExit exit =
        voluntaryExitGenerator.withEpoch(recentChainData.getBestState().orElseThrow(), 0, 3);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(REJECT);
  }
}
