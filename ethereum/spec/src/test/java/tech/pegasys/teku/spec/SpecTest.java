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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation.AttestationWorthinessCheckerAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecTest {

  @ParameterizedTest
  @EnumSource(
      value = SpecMilestone.class,
      names = {"MERGE"},
      mode = EnumSource.Mode.EXCLUDE)
  public void shouldCreateTheRightAttestationWorthinessChecker(SpecMilestone milestone) {
    final Spec spec;
    final DataStructureUtil dataStructureUtil;

    switch (milestone) {
      case PHASE0:
        spec = TestSpecFactory.createMainnetPhase0();
        dataStructureUtil = new DataStructureUtil(spec);
        assertThat(spec.createAttestationWorthinessChecker(dataStructureUtil.randomBeaconState()))
            .isInstanceOf(AttestationWorthinessCheckerAltair.NOOP.getClass());
        break;
      case ALTAIR:
        spec = TestSpecFactory.createMainnetAltair();
        dataStructureUtil = new DataStructureUtil(spec);
        assertThat(spec.createAttestationWorthinessChecker(dataStructureUtil.randomBeaconState()))
            .isInstanceOf(AttestationWorthinessCheckerAltair.class);
        break;
      default:
        throw new IllegalStateException("unsupported milestone");
    }
  }
}
