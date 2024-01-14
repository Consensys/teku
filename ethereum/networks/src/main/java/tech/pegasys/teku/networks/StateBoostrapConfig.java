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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;

public class StateBoostrapConfig {
  public static final String FINALIZED_STATE_URL_PATH = "eth/v2/debug/beacon/states/finalized";
  public static final String GENESIS_STATE_URL_PATH = "eth/v2/debug/beacon/states/genesis";

  private Optional<String> genesisState = Optional.empty();
  private Optional<String> initialState = Optional.empty();
  private Optional<String> checkpointSyncUrl = Optional.empty();
  private boolean isUsingCustomInitialState;
  private boolean allowSyncOutsideWeakSubjectivityPeriod;

  StateBoostrapConfig() {}

  public void setGenesisState(final String genesisState) {
    checkNotNull(genesisState);
    this.genesisState = Optional.of(genesisState);
  }

  public void setInitialState(final String initialState) {
    checkNotNull(initialState);
    this.initialState = Optional.of(initialState);
    this.isUsingCustomInitialState = false;
  }

  public void setCustomInitialState(final String initialState) {
    checkNotNull(initialState);
    this.initialState = Optional.of(initialState);
    this.isUsingCustomInitialState = true;
  }

  public void setCheckpointSyncUrl(final String checkpointSyncUrl) {
    checkNotNull(checkpointSyncUrl);
    this.checkpointSyncUrl = Optional.of(checkpointSyncUrl);
    this.genesisState =
        Optional.of(UrlSanitizer.appendPath(checkpointSyncUrl, GENESIS_STATE_URL_PATH));
    this.initialState =
        Optional.of(UrlSanitizer.appendPath(checkpointSyncUrl, FINALIZED_STATE_URL_PATH));
  }

  public void setAllowSyncOutsideWeakSubjectivityPeriod(
      final boolean allowSyncOutsideWeakSubjectivityPeriod) {
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
