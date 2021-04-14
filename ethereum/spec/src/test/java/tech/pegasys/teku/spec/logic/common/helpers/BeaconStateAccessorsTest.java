/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.Committee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateAccessorsTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final BeaconStateAccessors beaconStateAccessors = genesisSpec.beaconStateAccessors();
  private final SpecConfig specConfig = spec.atSlot(UInt64.ZERO).getConfig();

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState = createBeaconState().updated(state -> state.setSlot(GENESIS_SLOT));
    assertEquals(GENESIS_EPOCH, beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState =
        createBeaconState()
            .updated(state -> state.setSlot(GENESIS_SLOT.plus(specConfig.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH, beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState =
        createBeaconState()
            .updated(state -> state.setSlot(GENESIS_SLOT.plus(2L * specConfig.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH.increment(), beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    Committee committee = new Committee(UInt64.ONE, Arrays.asList(0, 1, 2));

    // Calculate Expected Results
    UInt64 expectedBalance = UInt64.ZERO;
    for (UInt64 balance : state.getBalances().asListUnboxed()) {
      if (balance.isLessThan(specConfig.getMaxEffectiveBalance())) {
        expectedBalance = expectedBalance.plus(balance);
      } else {
        expectedBalance = expectedBalance.plus(specConfig.getMaxEffectiveBalance());
      }
    }

    UInt64 totalBalance = beaconStateAccessors.getTotalBalance(state, committee.getCommittee());
    assertEquals(expectedBalance, totalBalance);
  }

  private BeaconState createBeaconState() {
    return new BeaconStateTestBuilder(dataStructureUtil)
        .forkVersion(specConfig.getGenesisForkVersion())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .build();
  }
}
