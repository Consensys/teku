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

package tech.pegasys.teku.spec.logic.versions.electra.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;
import static tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator.SAFETY_DECAY;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WeakSubjectivityCalculatorElectraTest {

  private final Spec spec =
      TestSpecFactory.createMinimalElectra(
          builder -> builder.electraBuilder(e -> e.minPerEpochChurnLimitElectra(ETH_TO_GWEI)));

  @Test
  public void computeWeakSubjectivityPeriod() {
    final BeaconState state =
        createStateWithSingleActiveConsolidatingValidator(spec, ETH_TO_GWEI.times(2048));
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final BeaconStateAccessorsElectra accessors =
        BeaconStateAccessorsElectra.required(specVersion.beaconStateAccessors());
    final UInt64 totalActiveBalance = accessors.getTotalActiveBalance(state);
    final UInt64 expected =
        computeExpectedWeakSubjectivityPeriod(
            specVersion.getConfig(),
            totalActiveBalance,
            accessors.getBalanceChurnLimit(BeaconStateElectra.required(state)));

    final WeakSubjectivityCalculator calculator = specVersion.weakSubjectivityCalculator();

    assertThat(calculator.computeWeakSubjectivityPeriod(state)).isEqualTo(expected);
  }

  protected BeaconState createStateWithSingleActiveConsolidatingValidator(
      final Spec spec, final UInt64 balance) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    return new BeaconStateTestBuilder(dataStructureUtil)
        .slot(0)
        .activeConsolidatingValidator(balance)
        .build();
  }

  protected UInt64 computeExpectedWeakSubjectivityPeriod(
      final SpecConfig specConfig, final UInt64 totalActiveBalance, final UInt64 delta) {
    return SAFETY_DECAY
        .times(totalActiveBalance)
        .dividedBy(delta.times(2).times(100))
        .plus(specConfig.getMinValidatorWithdrawabilityDelay());
  }
}
