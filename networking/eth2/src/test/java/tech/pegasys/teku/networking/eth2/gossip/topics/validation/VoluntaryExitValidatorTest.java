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
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.VALID;
import static tech.pegasys.teku.statetransition.BeaconChainUtil.initializeStorage;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.TestDepositGenerator;
import tech.pegasys.teku.core.VoluntaryExitGenerator;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
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
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(VALID);
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

    assertThat(voluntaryExitValidator.validate(exit1)).isEqualTo(VALID);
    assertThat(voluntaryExitValidator.validate(exit2)).isEqualTo(INVALID);
    assertThat(voluntaryExitValidator.validate(exit3)).isEqualTo(INVALID);
  }

  @Test
  public void shouldReturnInvalidForExitWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit1 =
        voluntaryExitGenerator.withInvalidSignature(
            recentChainData.getBestState().orElseThrow(), 3);
    assertThat(voluntaryExitValidator.validate(exit1)).isEqualTo(INVALID);
  }

  @Test
  public void shouldReturnInvalidForExitOfInactiveValidator() throws Exception {
    TestDepositGenerator testDepositGenerator =
        new TestDepositGenerator(VALIDATOR_KEYS.subList(0, 20));
    List<Deposit> deposits = testDepositGenerator.getDeposits(10, 20, 20);
    initializeStorage(recentChainData, VALIDATOR_KEYS.subList(0, 10));

    beaconChainUtil.setEth1DataOfChain(
        new Eth1Data(testDepositGenerator.getRoot(), UnsignedLong.valueOf(20), Bytes32.ZERO));
    beaconChainUtil.createAndImportBlockAtSlotWithDeposits(UnsignedLong.valueOf(6), deposits);

    SignedVoluntaryExit exit =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 15);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(INVALID);
  }

  @Test
  public void shouldReturnInvalidForExitWithInvalidValidatorIndex() throws Exception {
    initializeStorage(recentChainData, VALIDATOR_KEYS.subList(0, 10));
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedVoluntaryExit exit =
        voluntaryExitGenerator.valid(recentChainData.getBestState().orElseThrow(), 20, false);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(INVALID);
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
    assertThat(voluntaryExitValidator.validate(exit2)).isEqualTo(INVALID);
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
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(INVALID);
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
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(INVALID);
  }
}
