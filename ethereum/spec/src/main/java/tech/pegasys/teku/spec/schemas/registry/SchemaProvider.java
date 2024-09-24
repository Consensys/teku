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

import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;

import java.util.EnumSet;
import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

interface SchemaProvider<T> {
  Set<SpecMilestone> ALL_MILESTONES = EnumSet.allOf(SpecMilestone.class);
  Set<SpecMilestone> FROM_BELLATRIX = from(BELLATRIX);
  Set<SpecMilestone> FROM_CAPELLA = from(CAPELLA);
  Set<SpecMilestone> FROM_DENEB = from(DENEB);
  Set<SpecMilestone> FROM_ELECTRA = from(ELECTRA);

  static Set<SpecMilestone> from(final SpecMilestone milestone) {
    return EnumSet.copyOf(SpecMilestone.getAllMilestonesFrom(milestone));
  }

  static Set<SpecMilestone> fromTo(
      final SpecMilestone fromMilestone, final SpecMilestone toMilestone) {
    return EnumSet.copyOf(
        SpecMilestone.getAllMilestonesFrom(fromMilestone).stream()
            .filter(toMilestone::isLessThanOrEqualTo)
            .toList());
  }

  T getSchema(SchemaRegistry registry);

  Set<SpecMilestone> getSupportedMilestones();

  SpecMilestone getEffectiveMilestone(SpecMilestone version);

  SchemaId<T> getSchemaId();
}
