/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateAccessorsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateAccessorsGloas beaconStateAccessors =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());

  @Test
  void getBuilderIndex_shouldReturnBuilderIndex() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    assertThat(state.getBuilders()).hasSizeGreaterThan(5);
    for (int i = 0; i < 5; i++) {
      final Builder builder = state.getBuilders().get(i);
      assertThat(beaconStateAccessors.getBuilderIndex(state, builder.getPublicKey())).contains(i);
    }
  }

  @Test
  public void getBuilderIndex_shouldReturnEmptyWhenBuilderNotFound() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<Integer> index =
        beaconStateAccessors.getBuilderIndex(state, dataStructureUtil.randomPublicKey());
    assertThat(index).isEmpty();
  }
}
