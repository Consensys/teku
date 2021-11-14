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

package tech.pegasys.teku.spec.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

@TestSpecContext(
    milestone = {SpecMilestone.ALTAIR, SpecMilestone.MERGE},
    doNotGenerateSpec = true)
public class StateUpgradeTransitionTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      Collections.unmodifiableList(new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 3));

  private final UInt64 milestoneTransitionEpoch = UInt64.ONE;
  private UInt64 milestoneTransitionSlot;

  private BeaconState genesis;
  private StateTransition stateTransition;

  private Class<? extends BeaconState> beforeBeaconStateClass;
  private Class<? extends BeaconState> afterBeaconStateClass;

  @BeforeEach
  public void setup(SpecContext specContext) {
    Spec spec;
    switch (specContext.getSpecMilestone()) {
      case ALTAIR:
        spec = TestSpecFactory.createMinimalWithAltairForkEpoch(milestoneTransitionEpoch);
        beforeBeaconStateClass = BeaconStatePhase0.class;
        afterBeaconStateClass = BeaconStateAltair.class;
        break;
      case MERGE:
        spec = TestSpecFactory.createMinimalWithMergeForkEpoch(milestoneTransitionEpoch);
        beforeBeaconStateClass = BeaconStateAltair.class;
        afterBeaconStateClass = BeaconStateMerge.class;
        break;
      default:
        throw new IllegalArgumentException("unsupported milestone");
    }

    genesis = createGenesis(spec);
    stateTransition = new StateTransition(spec::atSlot);
    milestoneTransitionSlot = spec.computeStartSlotAtEpoch(milestoneTransitionEpoch);
  }

  @TestTemplate
  public void processSlots_acrossAltairFork_slotBySlot() throws Exception {
    BeaconState currentState = genesis;
    for (int i = 1; i <= milestoneTransitionSlot.intValue(); i++) {
      currentState = stateTransition.processSlots(currentState, UInt64.valueOf(i));
      assertThat(currentState.getSlot()).isEqualTo(UInt64.valueOf(i));
      if (i == milestoneTransitionSlot.intValue()) {
        assertThat(currentState).isInstanceOf(afterBeaconStateClass);
      } else {
        assertThat(currentState).isInstanceOf(beforeBeaconStateClass);
      }
    }
  }

  @TestTemplate
  public void processSlots_acrossAltairFork_acrossManySlots() throws Exception {
    final BeaconState result = stateTransition.processSlots(genesis, milestoneTransitionSlot);
    assertThat(result).isInstanceOf(afterBeaconStateClass);
    assertThat(result.getSlot()).isEqualTo(milestoneTransitionSlot);
  }

  @TestTemplate
  public void processSlots_pastAltairFork() throws Exception {
    final UInt64 targetSlot = milestoneTransitionSlot.plus(9);
    final BeaconState result = stateTransition.processSlots(genesis, targetSlot);
    assertThat(result).isInstanceOf(afterBeaconStateClass);
    assertThat(result.getSlot()).isEqualTo(targetSlot);
  }

  private BeaconState createGenesis(final Spec spec) {
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(spec, true))
            .createDeposits(VALIDATOR_KEYS, spec.getGenesisSpecConfig().getMaxEffectiveBalance());

    final List<DepositWithIndex> deposits = new ArrayList<>();
    for (int index = 0; index < initialDepositData.size(); index++) {
      final DepositData data = initialDepositData.get(index);
      DepositWithIndex deposit = new DepositWithIndex(data, UInt64.valueOf(index));
      deposits.add(deposit);
    }
    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(Bytes32.ZERO, UInt64.ZERO, deposits, Optional.empty());
    return initialState.updated(state -> state.setGenesis_time(UInt64.ZERO));
  }
}
