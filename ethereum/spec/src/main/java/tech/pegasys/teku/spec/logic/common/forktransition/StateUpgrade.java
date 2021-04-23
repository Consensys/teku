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

package tech.pegasys.teku.spec.logic.common.forktransition;

import java.util.Optional;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.altair.forktransition.AltairStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public interface StateUpgrade<T extends BeaconState> {

  T upgrade(BeaconState state);

  static Optional<StateUpgrade<?>> create(final SpecVersion spec) {
    switch (spec.getMilestone()) {
      case ALTAIR:
        final SpecConfigAltair specConfig = SpecConfigAltair.required(spec.getConfig());
        final SchemaDefinitionsAltair schemaDefinitions =
            SchemaDefinitionsAltair.required(spec.getSchemaDefinitions());
        final BeaconStateAccessorsAltair beaconStateAccessors =
            (BeaconStateAccessorsAltair) spec.beaconStateAccessors();
        return Optional.of(
            new AltairStateUpgrade(specConfig, schemaDefinitions, beaconStateAccessors));
      default:
        return Optional.empty();
    }
  }
}
