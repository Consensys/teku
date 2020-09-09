/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1.node;

import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Syncing {
  @JsonProperty("head_slot")
  @Schema(type = "string", pattern = PATTERN_UINT64, description = "Beacon node's head slot")
  public final UInt64 headSlot;

  @JsonProperty("sync_distance")
  @Schema(
      type = "string",
      pattern = PATTERN_UINT64,
      description = "How many slots node needs to process to reach head. 0 if synced.")
  public final UInt64 syncDistance;

  @JsonCreator
  public Syncing(
      @JsonProperty("head_slot") final UInt64 headSlot,
      @JsonProperty("sync_distance") final UInt64 syncDistance) {
    this.headSlot = headSlot;
    this.syncDistance = syncDistance;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Syncing syncing = (Syncing) o;
    return Objects.equals(headSlot, syncing.headSlot)
        && Objects.equals(syncDistance, syncing.syncDistance);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headSlot, syncDistance);
  }
}
