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
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.VALID;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.VoluntaryExitGenerator;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class VoluntaryExitValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 64);
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
  private final BeaconChainUtil beaconChainUtil =
      BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);
  private final VoluntaryExitGenerator voluntaryExitGenerator =
      new VoluntaryExitGenerator(VALIDATOR_KEYS);

  private VoluntaryExitValidator voluntaryExitValidator;

  @BeforeAll
  static void beforeAll() {
    Constants.SLOTS_PER_EPOCH = 2;
    Constants.PERSISTENT_COMMITTEE_PERIOD = 2;
  }

  @AfterAll
  static void afterAll() {
    Constants.setConstants("minimal");
  }

  @BeforeEach
  void beforeEach() {
    beaconChainUtil.initializeStorage();
    voluntaryExitValidator = new VoluntaryExitValidator(recentChainData);
  }

  @Test
  public void shouldReturnValidForValidVoluntaryExit() {
    beaconChainUtil.setSlot(UnsignedLong.valueOf(6));
    SignedVoluntaryExit exit =
        voluntaryExitGenerator.generateVoluntaryExit(
            recentChainData.getBestState().orElseThrow(), 3);
    voluntaryExitValidator.validate(exit);
    assertThat(voluntaryExitValidator.validate(exit)).isEqualTo(VALID);
  }
}
