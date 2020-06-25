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

package tech.pegasys.teku.core;

import com.google.common.primitives.UnsignedLong;
import java.util.List;

public class Deltas {
  private final List<UnsignedLong> rewards;
  private final List<UnsignedLong> penalties;

  public Deltas(final List<UnsignedLong> rewards, final List<UnsignedLong> penalties) {
    this.rewards = rewards;
    this.penalties = penalties;
  }

  public UnsignedLong getReward(final int validatorIndex) {
    return rewards.get(validatorIndex);
  }

  public UnsignedLong getPenalty(final int validatorIndex) {
    return penalties.get(validatorIndex);
  }
}
