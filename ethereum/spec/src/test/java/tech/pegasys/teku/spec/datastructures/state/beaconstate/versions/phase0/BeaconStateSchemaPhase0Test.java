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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.constants.TestConstantsLoader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchemaTest;

public class BeaconStateSchemaPhase0Test
    extends AbstractBeaconStateSchemaTest<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @Override
  protected BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> getSchema(
      final SpecConstants specConstants) {
    return BeaconStateSchemaPhase0.create(specConstants);
  }

  @Override
  protected BeaconStatePhase0 randomState() {
    return dataStructureUtil.stateBuilderPhase0().build();
  }

  @Test
  public void changeSpecConstantsTest_checkPhase0Fields() {
    final Spec standardSpec = SpecFactory.createMinimal();
    final SpecConstants modifiedConstants =
        TestConstantsLoader.loadConstantsBuilder("minimal").maxAttestations(123).build();

    BeaconStatePhase0 s1 = getSchema(modifiedConstants).createEmpty();
    BeaconStatePhase0 s2 = getSchema(standardSpec.getGenesisSpecConstants()).createEmpty();

    assertThat(s1.getPrevious_epoch_attestations().getMaxSize())
        .isNotEqualTo(s2.getPrevious_epoch_attestations().getMaxSize());
    assertThat(s1.getCurrent_epoch_attestations().getMaxSize())
        .isNotEqualTo(s2.getCurrent_epoch_attestations().getMaxSize());
  }
}
