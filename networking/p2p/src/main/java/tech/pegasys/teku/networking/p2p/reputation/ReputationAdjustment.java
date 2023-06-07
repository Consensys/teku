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

package tech.pegasys.teku.networking.p2p.reputation;

public enum ReputationAdjustment {
  LARGE_PENALTY(-DefaultReputationManager.LARGE_CHANGE),
  SMALL_PENALTY(-DefaultReputationManager.SMALL_CHANGE),
  SMALL_REWARD(DefaultReputationManager.SMALL_CHANGE),
  LARGE_REWARD(DefaultReputationManager.LARGE_CHANGE);

  private final int scoreChange;

  ReputationAdjustment(final int scoreChange) {
    this.scoreChange = scoreChange;
  }

  int getScoreChange() {
    return scoreChange;
  }
}
