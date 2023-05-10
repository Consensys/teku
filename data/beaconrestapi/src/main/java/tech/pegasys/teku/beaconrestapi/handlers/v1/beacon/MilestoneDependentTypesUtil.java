/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class MilestoneDependentTypesUtil {

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getAvailableSchemaDefinitionForAllMilestones(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, Optional<SszSchema<? extends T>>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate) {
    return getAvailableSchemaDefinitions(
        schemaDefinitionCache, title, schemaGetter, predicate, List.of(SpecMilestone.values()));
  }

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getAvailableSchemaDefinitionUpToMilestone(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, Optional<SszSchema<? extends T>>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate,
          final SpecMilestone milestone) {
    return getAvailableSchemaDefinitions(
        schemaDefinitionCache,
        title,
        schemaGetter,
        predicate,
        SpecMilestone.getMilestonesUpTo(milestone));
  }

  private static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getAvailableSchemaDefinitions(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, Optional<SszSchema<? extends T>>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate,
          final List<SpecMilestone> milestones) {
    final SerializableOneOfTypeDefinitionBuilder<T> builder =
        new SerializableOneOfTypeDefinitionBuilder<T>().title(title);
    for (SpecMilestone milestoneValue : milestones) {
      final Optional<SszSchema<? extends T>> schemaDefinition =
          schemaGetter.apply(schemaDefinitionCache.getSchemaDefinition(milestoneValue));
      schemaDefinition.ifPresent(
          sszSchema ->
              builder.withType(
                  value -> predicate.test(value, milestoneValue),
                  sszSchema.getJsonTypeDefinition()));
    }
    return builder.build();
  }

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getSchemaDefinitionForAllMilestones(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, SszSchema<? extends T>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate) {
    return getAvailableSchemaDefinitionForAllMilestones(
        schemaDefinitionCache, title, schemaGetter.andThen(Optional::of), predicate);
  }

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getSchemaDefinitionUpToMilestone(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, SszSchema<? extends T>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate,
          final SpecMilestone milestone) {
    return getAvailableSchemaDefinitionUpToMilestone(
        schemaDefinitionCache, title, schemaGetter.andThen(Optional::of), predicate, milestone);
  }

  public static <T extends SszData> DeserializableTypeDefinition<? extends T> slotBasedSelector(
      final String json,
      final SchemaDefinitionCache schemaDefinitionCache,
      final Function<SchemaDefinitions, SszSchema<? extends T>> getSchema) {
    try {
      final Optional<UInt64> slot =
          JsonUtil.getAttribute(json, CoreTypes.UINT64_TYPE, "message", "slot");
      final SpecMilestone milestone =
          schemaDefinitionCache.milestoneAtSlot(
              slot.orElseThrow(
                  () -> new BadRequestException("Could not locate slot in JSON data")));
      return getSchema
          .apply(schemaDefinitionCache.getSchemaDefinition(milestone))
          .getJsonTypeDefinition();
    } catch (final JsonProcessingException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
