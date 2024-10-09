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

package tech.pegasys.teku.spec.schemas.registry;

import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;

public class AttnetsENRFieldSchemaProvider
    extends AbstractSchemaProvider<SszBitvectorSchema<SszBitvector>> {
  public AttnetsENRFieldSchemaProvider() {
    super(ATTNETS_ENR_FIELD_SCHEMA);
    addMilestoneMapping(PHASE0, SpecMilestone.getHighestMilestone());
  }

  @Override
  protected SszBitvectorSchema<SszBitvector> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return SszBitvectorSchema.create(specConfig.getAttestationSubnetCount());
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return ALL_MILESTONES;
  }
}
