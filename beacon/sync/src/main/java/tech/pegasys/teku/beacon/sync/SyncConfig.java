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

package tech.pegasys.teku.beacon.sync;

import static com.google.common.base.Preconditions.checkNotNull;

public class SyncConfig {

  public static final boolean DEFAULT_MULTI_PEER_SYNC_ENABLED = true;
  public static final boolean DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED = false;
  public static final boolean DEFAULT_FETCH_ALL_HISTORIC_BLOCKS = true;

  private final boolean isEnabled;
  private final boolean isMultiPeerSyncEnabled;
  private final boolean reconstructHistoricStatesEnabled;
  private final boolean fetchAllHistoricBlocks;

  private SyncConfig(
      final boolean isEnabled,
      final boolean isMultiPeerSyncEnabled,
      final boolean reconstructHistoricStatesEnabled,
      final boolean fetchAllHistoricBlocks) {
    this.isEnabled = isEnabled;
    this.isMultiPeerSyncEnabled = isMultiPeerSyncEnabled;
    this.reconstructHistoricStatesEnabled = reconstructHistoricStatesEnabled;
    this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isSyncEnabled() {
    return isEnabled;
  }

  public boolean isMultiPeerSyncEnabled() {
    return isMultiPeerSyncEnabled;
  }

  public boolean isReconstructHistoricStatesEnabled() {
    return reconstructHistoricStatesEnabled;
  }

  public boolean fetchAllHistoricBlocks() {
    return fetchAllHistoricBlocks;
  }

  public static class Builder {
    private Boolean isEnabled;
    private Boolean isMultiPeerSyncEnabled = DEFAULT_MULTI_PEER_SYNC_ENABLED;
    private Boolean reconstructHistoricStatesEnabled = DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED;
    private boolean fetchAllHistoricBlocks = DEFAULT_FETCH_ALL_HISTORIC_BLOCKS;

    private Builder() {}

    public SyncConfig build() {
      initMissingDefaults();
      return new SyncConfig(
          isEnabled,
          isMultiPeerSyncEnabled,
          reconstructHistoricStatesEnabled,
          fetchAllHistoricBlocks);
    }

    private void initMissingDefaults() {
      if (isEnabled == null) {
        isEnabled = true;
      }
    }

    public Builder isSyncEnabled(final Boolean enabled) {
      checkNotNull(enabled);
      isEnabled = enabled;
      return this;
    }

    public Builder isSyncEnabledDefault(final boolean enabled) {
      if (isEnabled == null) {
        isEnabled = enabled;
      }
      return this;
    }

    public Builder isMultiPeerSyncEnabled(final Boolean multiPeerSyncEnabled) {
      checkNotNull(multiPeerSyncEnabled);
      isMultiPeerSyncEnabled = multiPeerSyncEnabled;
      return this;
    }

    public Builder reconstructHistoricStatesEnabled(
        final Boolean reconstructHistoricStatesEnabled) {
      checkNotNull(reconstructHistoricStatesEnabled);
      this.reconstructHistoricStatesEnabled = reconstructHistoricStatesEnabled;
      return this;
    }

    public Builder fetchAllHistoricBlocks(final boolean fetchAllHistoricBlocks) {
      this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;
      return this;
    }
  }
}
