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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

public class StateTransitionTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      Collections.unmodifiableList(new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 3));

  private final UInt64 altairTransitionSlot = UInt64.valueOf(8);
  private final Spec spec = TestSpecFactory.createMinimalWithAltairFork(altairTransitionSlot);

  private final BeaconState genesis = createGenesis(spec);
  private final StateTransition stateTransition = new StateTransition(spec::atSlot);

  @Test
  public void processSlots_acrossAltairFork_slotBySlot() throws Exception {
    BeaconState currentState = genesis;
    for (int i = 1; i <= altairTransitionSlot.intValue(); i++) {
      currentState = stateTransition.processSlots(currentState, UInt64.valueOf(i));
      assertThat(currentState.getSlot()).isEqualTo(UInt64.valueOf(i));
      if (i == altairTransitionSlot.intValue()) {
        assertThat(currentState).isInstanceOf(BeaconStateAltair.class);
      } else {
        assertThat(currentState).isInstanceOf(BeaconStatePhase0.class);
      }
    }
  }

  @Test
  public void processSlots_acrossAltairFork_acrossManySlots() throws Exception {
    final BeaconState result = stateTransition.processSlots(genesis, altairTransitionSlot);
    assertThat(result).isInstanceOf(BeaconStateAltair.class);
    assertThat(result.getSlot()).isEqualTo(altairTransitionSlot);
  }

  @Test
  public void processSlots_pastAltairFork() throws Exception {
    final UInt64 targetSlot = altairTransitionSlot.plus(9);
    final BeaconState result = stateTransition.processSlots(genesis, targetSlot);
    assertThat(result).isInstanceOf(BeaconStateAltair.class);
    assertThat(result.getSlot()).isEqualTo(targetSlot);
  }

  private BeaconState createGenesis(final Spec spec) {
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(true))
            .createDeposits(VALIDATOR_KEYS, spec.getGenesisSpecConfig().getMaxEffectiveBalance());

    final List<DepositWithIndex> deposits = new ArrayList<>();
    for (int index = 0; index < initialDepositData.size(); index++) {
      final DepositData data = initialDepositData.get(index);
      DepositWithIndex deposit = new DepositWithIndex(data, UInt64.valueOf(index));
      deposits.add(deposit);
    }
    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(Bytes32.ZERO, UInt64.ZERO, deposits);
    return initialState.updated(state -> state.setGenesis_time(UInt64.ZERO));
  }
}
