/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.api;

import java.util.Objects;
import java.util.Set;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeSubnetSubscription {
  private final int validatorIndex;
  private final Set<Integer> syncCommitteeIndices;
  private final UInt64 untilEpoch;

  public SyncCommitteeSubnetSubscription(
      final int validatorIndex, final Set<Integer> syncCommitteeIndices, final UInt64 untilEpoch) {
    this.validatorIndex = validatorIndex;
    this.syncCommitteeIndices = syncCommitteeIndices;
    this.untilEpoch = untilEpoch;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public Set<Integer> getSyncCommitteeIndices() {
    return syncCommitteeIndices;
  }

  public UInt64 getUntilEpoch() {
    return untilEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SyncCommitteeSubnetSubscription that = (SyncCommitteeSubnetSubscription) o;
    return validatorIndex == that.validatorIndex
        && Objects.equals(syncCommitteeIndices, that.syncCommitteeIndices)
        && Objects.equals(untilEpoch, that.untilEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, syncCommitteeIndices, untilEpoch);
  }
}
