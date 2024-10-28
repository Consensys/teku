/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateTest;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateElectraTest
    extends AbstractBeaconStateTest<BeaconStateElectra, MutableBeaconStateElectra> {
  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalElectra();
  }

  @Override
  protected BeaconStateSchema<BeaconStateElectra, MutableBeaconStateElectra> getSchema(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return BeaconStateSchemaElectra.create(specConfig, schemaRegistry);
  }

  @Override
  protected BeaconStateElectra randomState() {
    return dataStructureUtil.stateBuilderElectra().build();
  }
}
