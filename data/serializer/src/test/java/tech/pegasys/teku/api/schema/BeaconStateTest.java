/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;

@TestSpecContext(allMilestones = true)
public class BeaconStateTest {

  @TestTemplate
  public void shouldConvertToInternalObject(SpecContext ctx) {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconStateInternal =
        ctx.getDataStructureUtil().randomBeaconState();
    final Spec spec = ctx.getSpec();
    final BeaconState beaconState;

    switch (spec.getGenesisSpec().getMilestone()) {
      case PHASE0:
        beaconState = new BeaconStatePhase0(beaconStateInternal);
        break;
      case ALTAIR:
        beaconState = new BeaconStateAltair(beaconStateInternal);
        break;
      case BELLATRIX:
      case CAPELLA: // TODO
        beaconState = new BeaconStateBellatrix(beaconStateInternal);
        break;
      default:
        throw new IllegalStateException("Unsupported milestone");
    }

    assertThat(beaconState.asInternalBeaconState(spec)).isEqualTo(beaconStateInternal);
  }
}
