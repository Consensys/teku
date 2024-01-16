/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.networks;

import java.util.Optional;

public class StateBoostrapConfig {
  private final Optional<String> genesisState;
  private final Optional<String> initialState;
  private final Optional<String> checkpointSyncUrl;
  private final boolean isUsingCustomInitialState;
  private final boolean allowSyncOutsideWeakSubjectivityPeriod;

  public StateBoostrapConfig(
      final Optional<String> genesisState,
      final Optional<String> initialState,
      final Optional<String> checkpointSyncUrl,
      final boolean isUsingCustomInitialState,
      final boolean allowSyncOutsideWeakSubjectivityPeriod) {
    this.checkpointSyncUrl = checkpointSyncUrl;
    this.genesisState = genesisState;
    this.initialState = initialState;
    this.isUsingCustomInitialState = isUsingCustomInitialState;
    this.allowSyncOutsideWeakSubjectivityPeriod = allowSyncOutsideWeakSubjectivityPeriod;
  }

  public Optional<String> getGenesisState() {
    return genesisState;
  }

  public Optional<String> getInitialState() {
    return initialState;
  }

  public Optional<String> getCheckpointSyncUrl() {
    return checkpointSyncUrl;
  }

  public boolean isUsingCustomInitialState() {
    return isUsingCustomInitialState;
  }

  public boolean isUsingCheckpointSync() {
    return checkpointSyncUrl.isPresent();
  }

  public boolean isAllowSyncOutsideWeakSubjectivityPeriod() {
    return allowSyncOutsideWeakSubjectivityPeriod;
  }
}
