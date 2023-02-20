/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.api.migrated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeRewardData {
  final boolean executionOptimistic;
  final boolean finalized;
  final Map<Integer, UInt64> rewardData;

  public SyncCommitteeRewardData(final boolean executionOptimistic, final boolean finalized) {
    this.executionOptimistic = executionOptimistic;
    this.finalized = finalized;
    this.rewardData = new HashMap<>();
  }

  public void increaseReward(final int validatorIndex, final UInt64 amount) {
    final UInt64 balance = rewardData.getOrDefault(validatorIndex, UInt64.ZERO);
    rewardData.put(validatorIndex, balance.plus(amount));
  }

  public void decreaseReward(final int validatorIndex, final UInt64 amount) {
    final UInt64 balance = rewardData.getOrDefault(validatorIndex, UInt64.ZERO);
    rewardData.put(validatorIndex, balance.minusMinZero(amount));
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  public boolean isFinalized() {
    return finalized;
  }

  public List<Map.Entry<Integer, UInt64>> getRewardData() {
    return new ArrayList<>(rewardData.entrySet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SyncCommitteeRewardData that = (SyncCommitteeRewardData) o;
    return executionOptimistic == that.executionOptimistic
        && finalized == that.finalized
        && Objects.equals(rewardData, that.rewardData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionOptimistic, finalized, rewardData);
  }

  @Override
  public String toString() {
    return "SyncCommitteeRewardData{"
        + "executionOptimistic="
        + executionOptimistic
        + ", finalized="
        + finalized
        + ", rewardData="
        + rewardData
        + '}';
  }
}
