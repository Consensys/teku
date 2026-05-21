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

package tech.pegasys.teku.spec.logic.versions.gloas.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.spec.logic.versions.electra.weaksubjectivity.WeakSubjectivityCalculatorElectraTest;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;

class WeakSubjectivityCalculatorGloasTest extends WeakSubjectivityCalculatorElectraTest {

  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          builder ->
              builder
                  .electraBuilder(e -> e.minPerEpochChurnLimitElectra(ETH_TO_GWEI))
                  .gloasBuilder(
                      g ->
                          g.churnLimitQuotientGloas(65_536)
                              .consolidationChurnLimitQuotient(65_536)
                              .maxPerEpochActivationChurnLimitGloas(ETH_TO_GWEI.times(4096))));

  @Override
  @Test
  public void computeWeakSubjectivityPeriod() {
    final BeaconState state =
        createStateWithSingleActiveConsolidatingValidator(spec, ETH_TO_GWEI.times(2048));
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final BeaconStateAccessorsGloas accessors =
        BeaconStateAccessorsGloas.required(specVersion.beaconStateAccessors());
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    final UInt64 delta =
        accessors
            .getExitChurnLimit(stateElectra)
            .times(2)
            .dividedBy(3)
            .plus(accessors.getActivationChurnLimit(stateElectra).dividedBy(3))
            .plus(accessors.getConsolidationChurnLimit(stateElectra));
    final UInt64 totalActiveBalance = accessors.getTotalActiveBalance(state);
    final UInt64 expected =
        computeExpectedWeakSubjectivityPeriod(specVersion.getConfig(), totalActiveBalance, delta);

    final WeakSubjectivityCalculator calculator = specVersion.weakSubjectivityCalculator();

    assertThat(calculator.computeWeakSubjectivityPeriod(state)).isEqualTo(expected);
  }
}
