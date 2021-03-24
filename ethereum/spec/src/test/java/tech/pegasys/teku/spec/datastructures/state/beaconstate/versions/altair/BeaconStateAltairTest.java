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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateTest;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class BeaconStateAltairTest
    extends AbstractBeaconStateTest<BeaconStateAltair, MutableBeaconStateAltair> {

  @Override
  protected BeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> getSchema(
      final SpecConfig specConfig) {
    return BeaconStateSchemaAltair.create(specConfig);
  }

  @Override
  protected BeaconStateAltair randomState() {
    return dataStructureUtil.stateBuilderAltair().build();
  }

  @Test
  public void clearAndModifyCurrentEpochParticipation() {
    final int updatedValueCount = 5;
    final SszByte newValue = SszByte.of(1);
    final BeaconStateAltair state = randomState();

    final SszList<SszByte> originalValue = state.getCurrentEpochParticipation();
    // Modify the state
    final BeaconStateAltair updated =
        state.updatedAltair(
            s -> {
              // Clear
              s.clearCurrentEpochParticipation();
              assertThat(s.getCurrentEpochParticipation().size()).isEqualTo(0);
              assertThat(s.getCurrentEpochParticipation()).isNotEqualTo(originalValue);

              // Now set some values
              s.getCurrentEpochParticipation().setAll(newValue, updatedValueCount);
              assertThat(s.getCurrentEpochParticipation().size()).isEqualTo(5);
            });

    assertThat(updated.getCurrentEpochParticipation().size()).isEqualTo(5);
    assertThat(updated.getCurrentEpochParticipation()).isNotEqualTo(originalValue);
    for (int i = 0; i < updatedValueCount; i++) {
      assertThat(updated.getCurrentEpochParticipation().get(i)).isEqualTo(newValue);
    }
  }
}
