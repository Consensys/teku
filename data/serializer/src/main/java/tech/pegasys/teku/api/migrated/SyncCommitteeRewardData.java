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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeRewardData {
  final Map<Integer, UInt64> rewardData;

  public SyncCommitteeRewardData() {
    this.rewardData = new HashMap<>();
  }

  public void updateReward(final int validatorIndex, final UInt64 amount) {
    final UInt64 balance = rewardData.getOrDefault(validatorIndex, UInt64.ZERO);
    rewardData.put(validatorIndex, balance.plus(amount));
  }

  public List<Map.Entry<Integer, UInt64>> getRewardData() {
    return new ArrayList<>(rewardData.entrySet());
  }
}
