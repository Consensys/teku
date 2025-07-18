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

package tech.pegasys.teku.beaconrestapi.addon;

import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.RestApiBuilderAddon;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetDataColumnSidecars;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class FuluRestApiBuilderAddon implements RestApiBuilderAddon {

  final Spec spec;
  final DataProvider dataProvider;
  final SchemaDefinitionCache schemaCache;

  public FuluRestApiBuilderAddon(
      final Spec spec, final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this.spec = spec;
    this.dataProvider = dataProvider;
    this.schemaCache = schemaCache;
  }

  @Override
  public boolean isEnabled() {
    return spec.isMilestoneSupported(SpecMilestone.FULU);
  }

  @Override
  public RestApiBuilder apply(final RestApiBuilder builder) {
    return builder.endpoint(new GetDataColumnSidecars(dataProvider, schemaCache));
  }
}
