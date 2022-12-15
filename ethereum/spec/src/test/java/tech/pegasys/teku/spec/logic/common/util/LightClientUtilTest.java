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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class LightClientUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final LightClientUtil lightClientUtil = spec.getLightClientUtilRequired(UInt64.ZERO);

  @Test
  public void getBoostrap_shouldReturnValidBootstrap() {
    BeaconState state = dataStructureUtil.stateBuilderAltair().build();
    BeaconBlockHeader expectedHeader = BeaconBlockHeader.fromState(state);
    LightClientBootstrap bootstrap = lightClientUtil.getLightClientBootstrap(state);

    assertThat(bootstrap.getBeaconBlockHeader()).isEqualTo(expectedHeader);
    assertThat(bootstrap.getCurrentSyncCommittee())
        .isEqualTo(BeaconStateAltair.required(state).getCurrentSyncCommittee());
  }
}
