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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;

/**
 * Rebuilds list-like BeaconState fields through a target state schema during fork upgrades. When
 * the destination schema represents a field differently from the source (e.g. a bounded list
 * becoming a progressive list in Gloas), the source backing node is structurally incompatible, so
 * the field must be rematerialised from its elements through the target field schema.
 */
public final class BeaconStateListFieldMigration {

  private BeaconStateListFieldMigration() {}

  public static <T extends SszData> SszList<T> toTargetList(
      final SszListSchema<T, ?> targetSchema, final SszList<T> source) {
    return targetSchema.createFromElements(source.stream().toList());
  }

  public static SszUInt64List toTargetUInt64List(
      final SszUInt64ListSchema<?> targetSchema, final SszUInt64List source) {
    return targetSchema.createFromElements(source.stream().toList());
  }

  @SuppressWarnings("unchecked")
  public static <T extends SszData> SszList<T> rematerialize(
      final BeaconStateSchema<?, ?> stateSchema,
      final BeaconStateFields field,
      final SszList<T> source) {
    final SszListSchema<T, ?> targetSchema =
        (SszListSchema<T, ?>)
            stateSchema.getChildSchema(stateSchema.getFieldIndex(field.getSszFieldName()));
    return toTargetList(targetSchema, source);
  }

  public static SszUInt64List rematerializeUInt64(
      final BeaconStateSchema<?, ?> stateSchema,
      final BeaconStateFields field,
      final SszUInt64List source) {
    final SszUInt64ListSchema<?> targetSchema =
        (SszUInt64ListSchema<?>)
            stateSchema.getChildSchema(stateSchema.getFieldIndex(field.getSszFieldName()));
    return toTargetUInt64List(targetSchema, source);
  }
}
