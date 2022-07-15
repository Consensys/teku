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

package tech.pegasys.teku.validator.api.required;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncingStatus {

  private final UInt64 headSlot;
  private final UInt64 syncDistance;
  private final boolean isSyncing;
  private final Optional<Boolean> isOptimistic;

  public SyncingStatus(
      final UInt64 headSlot,
      final UInt64 syncDistance,
      final boolean isSyncing,
      final Optional<Boolean> isOptimistic) {
    this.headSlot = headSlot;
    this.syncDistance = syncDistance;
    this.isSyncing = isSyncing;
    this.isOptimistic = isOptimistic;
  }

  public UInt64 getHeadSlot() {
    return headSlot;
  }

  public UInt64 getSyncDistance() {
    return syncDistance;
  }

  public boolean isSyncing() {
    return isSyncing;
  }

  public Optional<Boolean> getIsOptimistic() {
    return isOptimistic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SyncingStatus that = (SyncingStatus) o;
    return isSyncing == that.isSyncing
        && Objects.equals(headSlot, that.headSlot)
        && Objects.equals(syncDistance, that.syncDistance)
        && Objects.equals(isOptimistic, that.isOptimistic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headSlot, syncDistance, isSyncing, isOptimistic);
  }

  @Override
  public String toString() {
    return "SyncingStatus{"
        + "headSlot="
        + headSlot
        + ", syncDistance="
        + syncDistance
        + ", isSyncing="
        + isSyncing
        + ", isOptimistic="
        + isOptimistic
        + '}';
  }

  public static class Builder {

    private UInt64 headSlot;
    private UInt64 syncDistance;
    private boolean isSyncing;
    private Optional<Boolean> isOptimistic;

    private Builder() {}

    public static Builder builder() {
      return new Builder();
    }

    public Builder headSlot(UInt64 headSlot) {
      this.headSlot = headSlot;
      return this;
    }

    public Builder syncDistance(UInt64 syncDistance) {
      this.syncDistance = syncDistance;
      return this;
    }

    public Builder isSyncing(boolean isSyncing) {
      this.isSyncing = isSyncing;
      return this;
    }

    public Builder isOptimistic(Optional<Boolean> isOptimistic) {
      this.isOptimistic = isOptimistic;
      return this;
    }

    public SyncingStatus build() {
      return new SyncingStatus(headSlot, syncDistance, isSyncing, isOptimistic);
    }
  }
}
