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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.heze;

import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaHeze extends BeaconStateSchemaGloas {

  private BeaconStateSchemaHeze(
      final SpecConfigHeze specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateHeze", specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaHeze create(
      final SpecConfigHeze specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaHeze(specConfig, schemaRegistry);
  }
}
