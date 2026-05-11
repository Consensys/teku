/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateMutatorsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigGloas configGloas = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());
  private final SchemaDefinitionsGloas schemaDefinitionsGloas =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  private BeaconStateMutatorsGloas stateMutatorsGloas;

  @BeforeEach
  public void setUp() {
    stateMutatorsGloas =
        BeaconStateMutatorsGloas.required(spec.getGenesisSpec().beaconStateMutators());
    // Sanity: the mutator wired by the spec must be the Gloas variant we are testing.
    assertThat(stateMutatorsGloas).isNotNull();
    assertThat(stateMutatorsGloas).isInstanceOf(BeaconStateMutators.class);
    assertThat(schemaDefinitionsGloas).isNotNull();
  }

  @Test
  public void computeExitEpochAndUpdateChurn_shouldUseUncappedExitChurnAboveActivationCap() {
    // 65 validators × 32 ETH → exit churn limit = 130 ETH (uncapped), well above the activation
    // cap of 128 ETH. A 32 ETH exit must consume 32 ETH from the 130 ETH-per-epoch budget without
    // pushing the earliest exit epoch beyond the freshly-computed activation-exit epoch.
    final BeaconStateElectra preState = activeStateWithValidators(65);
    final UInt64 perEpochExitChurn = beaconStateAccessorsGloas.getExitChurnLimit(preState);
    assertThat(perEpochExitChurn)
        .isGreaterThan(configGloas.getMaxPerEpochActivationChurnLimitGloas());

    final UInt64 expectedEarliestExitEpoch = computedActivationExitEpoch(preState);
    final UInt64 exitBalance = UInt64.THIRTY_TWO_ETH;

    final BeaconStateElectra postState =
        preState.updatedElectra(
            state -> stateMutatorsGloas.computeExitEpochAndUpdateChurn(state, exitBalance));

    assertThat(postState.getEarliestExitEpoch()).isEqualTo(expectedEarliestExitEpoch);
    assertThat(postState.getExitBalanceToConsume())
        .isEqualTo(perEpochExitChurn.minusMinZero(exitBalance));
  }

  @Test
  public void computeExitEpochAndUpdateChurn_shouldBumpEarliestExitEpochWhenBalanceExceedsBudget() {
    // 65 validators × 32 ETH → exit churn = 130 ETH/epoch. Exiting just under 2× the budget must
    // advance earliestExitEpoch by exactly 1 above the freshly-computed activation-exit epoch.
    final BeaconStateElectra preState = activeStateWithValidators(65);
    final UInt64 perEpochExitChurn = beaconStateAccessorsGloas.getExitChurnLimit(preState);
    final UInt64 expectedEarliestExitEpoch = computedActivationExitEpoch(preState);
    final UInt64 exitBalance = perEpochExitChurn.times(UInt64.valueOf(2)).minusMinZero(UInt64.ONE);

    final BeaconStateElectra postState =
        preState.updatedElectra(
            state -> stateMutatorsGloas.computeExitEpochAndUpdateChurn(state, exitBalance));

    assertThat(postState.getEarliestExitEpoch()).isEqualTo(expectedEarliestExitEpoch.plus(1));
  }

  private UInt64 computedActivationExitEpoch(final BeaconStateElectra state) {
    return spec.getGenesisSpec()
        .miscHelpers()
        .computeActivationExitEpoch(beaconStateAccessorsGloas.getCurrentEpoch(state));
  }

  private BeaconStateElectra activeStateWithValidators(final int validatorCount) {
    final BeaconStateTestBuilder builder = new BeaconStateTestBuilder(dataStructureUtil).slot(0);
    IntStream.range(0, validatorCount)
        .forEach(__ -> builder.activeValidator(UInt64.THIRTY_TWO_ETH));
    return BeaconStateElectra.required(builder.build());
  }
}
