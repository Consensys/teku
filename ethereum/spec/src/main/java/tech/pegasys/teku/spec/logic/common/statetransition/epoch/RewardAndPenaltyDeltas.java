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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RewardAndPenaltyDeltas {
  private final List<RewardAndPenalty> deltas;

  public RewardAndPenaltyDeltas(final int validatorCount) {
    this.deltas = new ArrayList<>(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      deltas.add(new RewardAndPenalty());
    }
  }

  public RewardAndPenalty getDelta(final int validatorIndex) {
    return deltas.get(validatorIndex);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("deltas", deltas).toString();
  }

  public static class RewardAndPenalty {
    private UInt64 reward = UInt64.ZERO;
    private UInt64 penalty = UInt64.ZERO;

    public void reward(final UInt64 amount) {
      reward = reward.plus(amount);
    }

    public void penalize(final UInt64 amount) {
      penalty = penalty.plus(amount);
    }

    public void add(final RewardAndPenalty other) {
      reward(other.reward);
      penalize(other.penalty);
    }

    public UInt64 getReward() {
      return reward;
    }

    public UInt64 getPenalty() {
      return penalty;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final RewardAndPenalty delta = (RewardAndPenalty) o;
      return Objects.equals(reward, delta.reward) && Objects.equals(penalty, delta.penalty);
    }

    @Override
    public int hashCode() {
      return Objects.hash(reward, penalty);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("reward", reward)
          .add("penalty", penalty)
          .toString();
    }
  }
}
