/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.schemas.providers;

import static tech.pegasys.teku.spec.schemas.SchemaTypes.BLINDED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SIGNED_BLINDED_BEACON_BLOCK_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;

public class SignedBlindedBeaconBlockSchemaProvider
    extends AbstractSchemaProvider<SignedBeaconBlockSchema> {

  public SignedBlindedBeaconBlockSchemaProvider() {
    super(SIGNED_BLINDED_BEACON_BLOCK_SCHEMA);
  }

  @Override
  protected SignedBeaconBlockSchema createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return new SignedBeaconBlockSchema(
        registry.get(BLINDED_BEACON_BLOCK_SCHEMA),
        "SignedBlindedBlock" + capitalizeMilestone(effectiveMilestone));
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return FROM_BELLATRIX;
  }
}
