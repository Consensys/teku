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

package tech.pegasys.teku.spec.schemas;

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;

public class SchemaDefinitionsPhase0 implements SchemaDefinitions {
  private final SpecConstants specConstants;

  public SchemaDefinitionsPhase0(final SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return BeaconStateSchemaPhase0.create(specConstants);
  }

  @Override
  public BeaconBlockBodySchema getBeaconBlockBodySchema() {
    return BeaconBlockBodySchema.create(specConstants); 
  }
}
