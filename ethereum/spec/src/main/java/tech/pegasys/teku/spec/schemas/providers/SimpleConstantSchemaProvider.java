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

import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.AbstractSchemaProvider;
import tech.pegasys.teku.spec.schemas.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.SchemaTypes.SchemaId;

public class SimpleConstantSchemaProvider<T> extends AbstractSchemaProvider<T> {
  private final Supplier<T> schemaSupplier;
  private final Set<SpecMilestone> supportedMilestones;

  private SimpleConstantSchemaProvider(
      final SchemaId<T> schemaId, final SpecMilestone from, final Supplier<T> schemaSupplier) {
    super(schemaId);
    this.schemaSupplier = schemaSupplier;
    this.supportedMilestones = new HashSet<>(SpecMilestone.getAllMilestonesFrom(from));
    addMilestoneMapping(PHASE0, SpecMilestone.getHighestMilestone());
  }

  static <T> SimpleConstantSchemaProvider<T> create(
      final SchemaId<T> schemaId, final Supplier<T> schemaSupplier) {
    return new SimpleConstantSchemaProvider<>(schemaId, PHASE0, schemaSupplier);
  }

  static <T> SimpleConstantSchemaProvider<T> createFrom(
      final SchemaId<T> schemaId, final SpecMilestone from, final Supplier<T> schemaSupplier) {
    return new SimpleConstantSchemaProvider<>(schemaId, from, schemaSupplier);
  }

  @Override
  protected T createSchema(
      final SchemaRegistry registry, final SpecMilestone baseVersion, final SpecConfig specConfig) {
    return schemaSupplier.get();
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return supportedMilestones;
  }
}
