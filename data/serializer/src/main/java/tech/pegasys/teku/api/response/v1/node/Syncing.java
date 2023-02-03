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

package tech.pegasys.teku.api.response.v1.node;

import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@JsonInclude(JsonInclude.Include.NON_NULL)
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

  @JsonProperty("is_syncing")
  @Schema(
      type = "boolean",
      description = "Set to true if the node is syncing, false if the node is synced.")
  public final boolean isSyncing;

  @JsonProperty("is_optimistic")
  @Schema(
      type = "boolean",
      description = "Set to true if the node is optimistically fetching blocks.")
  public final Boolean isOptimistic;

  @JsonProperty("el_offline")
  @Schema(type = "boolean", description = "Set to true if the execution client is offline.")
  public final Boolean elOffline;

  public Syncing(
      final UInt64 headSlot,
      final UInt64 syncDistance,
      final boolean isSyncing,
      final boolean elOffline) {
    this(headSlot, syncDistance, isSyncing, false, elOffline);
  }

  @JsonCreator
  public Syncing(
      @JsonProperty("head_slot") final UInt64 headSlot,
      @JsonProperty("sync_distance") final UInt64 syncDistance,
      @JsonProperty("is_syncing") final boolean isSyncing,
      @JsonProperty("is_optimistic") final boolean isOptimistic,
      @JsonProperty("el_offline") final boolean elOffline) {
    this.headSlot = headSlot;
    this.syncDistance = syncDistance;
    this.isSyncing = isSyncing;
    this.isOptimistic = isOptimistic;
    this.elOffline = elOffline;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Syncing syncing = (Syncing) o;
    return isSyncing == syncing.isSyncing
        && Objects.equals(headSlot, syncing.headSlot)
        && Objects.equals(syncDistance, syncing.syncDistance)
        && Objects.equals(isOptimistic, syncing.isOptimistic)
        && Objects.equals(elOffline, syncing.elOffline);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headSlot, syncDistance, isSyncing, isOptimistic, elOffline);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("headSlot", headSlot)
        .add("syncDistance", syncDistance)
        .add("isSyncing", isSyncing)
        .add("isOptimistic", isOptimistic)
        .add("elOffline", elOffline)
        .toString();
  }
}
