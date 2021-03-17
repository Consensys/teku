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

import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;

public class SchemaDefinitionsAltair implements SchemaDefinitions {
  private final SpecConfig specConfig;

  public SchemaDefinitionsAltair(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  @Override
  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return BeaconStateSchemaAltair.create(specConfig);
  }

  @Override
  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema() {
    // TODO(#3648) - Implement altair block body
    throw new NotImplementedException();
  }
}
