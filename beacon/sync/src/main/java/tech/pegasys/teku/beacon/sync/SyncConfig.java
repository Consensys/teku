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
  public static final int DEFAULT_FORWARD_SYNC_BATCH_SIZE = 50;
  public static final int DEFAULT_HISTORICAL_SYNC_BATCH_SIZE = 50;
  public static final int DEFAULT_FORWARD_SYNC_MAX_PENDING_BATCHES = 5;
  public static final int DEFAULT_FORWARD_SYNC_MAX_BLOCKS_PER_MINUTE = 500;

  private final boolean isEnabled;
  private final boolean isMultiPeerSyncEnabled;
  private final boolean reconstructHistoricStatesEnabled;
  private final boolean fetchAllHistoricBlocks;
  private final int historicalSyncBatchSize;
  private final int forwardSyncBatchSize;
  private final int forwardSyncMaxPendingBatches;
  private final int forwardSyncMaxBlocksPerMinute;

  private SyncConfig(
      final boolean isEnabled,
      final boolean isMultiPeerSyncEnabled,
      final boolean reconstructHistoricStatesEnabled,
      final boolean fetchAllHistoricBlocks,
      final int historicalSyncBatchSize,
      final int forwardSyncBatchSize,
      final int forwardSyncMaxPendingBatches,
      final int forwardSyncMaxBlocksPerMinute) {
    this.isEnabled = isEnabled;
    this.isMultiPeerSyncEnabled = isMultiPeerSyncEnabled;
    this.reconstructHistoricStatesEnabled = reconstructHistoricStatesEnabled;
    this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;
    this.historicalSyncBatchSize = historicalSyncBatchSize;
    this.forwardSyncBatchSize = forwardSyncBatchSize;
    this.forwardSyncMaxPendingBatches = forwardSyncMaxPendingBatches;
    this.forwardSyncMaxBlocksPerMinute = forwardSyncMaxBlocksPerMinute;
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

  public int getHistoricalSyncBatchSize() {
    return historicalSyncBatchSize;
  }

  public int getForwardSyncBatchSize() {
    return forwardSyncBatchSize;
  }

  public int getForwardSyncMaxPendingBatches() {
    return forwardSyncMaxPendingBatches;
  }

  public int getForwardSyncMaxBlocksPerMinute() {
    return forwardSyncMaxBlocksPerMinute;
  }

  public static class Builder {
    private Boolean isEnabled;
    private Boolean isMultiPeerSyncEnabled = DEFAULT_MULTI_PEER_SYNC_ENABLED;
    private Boolean reconstructHistoricStatesEnabled = DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED;
    private boolean fetchAllHistoricBlocks = DEFAULT_FETCH_ALL_HISTORIC_BLOCKS;
    private Integer historicalSyncBatchSize = DEFAULT_HISTORICAL_SYNC_BATCH_SIZE;
    private Integer forwardSyncBatchSize = DEFAULT_FORWARD_SYNC_BATCH_SIZE;
    private Integer forwardSyncMaxPendingBatches = DEFAULT_FORWARD_SYNC_MAX_PENDING_BATCHES;
    private Integer forwardSyncMaxBlocksPerMinute = DEFAULT_FORWARD_SYNC_MAX_BLOCKS_PER_MINUTE;

    private Builder() {}

    public SyncConfig build() {
      initMissingDefaults();
      return new SyncConfig(
          isEnabled,
          isMultiPeerSyncEnabled,
          reconstructHistoricStatesEnabled,
          fetchAllHistoricBlocks,
          historicalSyncBatchSize,
          forwardSyncBatchSize,
          forwardSyncMaxPendingBatches,
          forwardSyncMaxBlocksPerMinute);
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

    public Builder historicalSyncBatchSize(final Integer historicalSyncBatchSize) {
      checkNotNull(historicalSyncBatchSize);
      this.historicalSyncBatchSize = historicalSyncBatchSize;
      return this;
    }

    public Builder forwardSyncBatchSize(final Integer forwardSyncBatchSize) {
      checkNotNull(forwardSyncBatchSize);
      this.forwardSyncBatchSize = forwardSyncBatchSize;
      return this;
    }

    public Builder forwardSyncMaxPendingBatches(final Integer forwardSyncMaxPendingBatches) {
      checkNotNull(forwardSyncMaxPendingBatches);
      this.forwardSyncMaxPendingBatches = forwardSyncMaxPendingBatches;
      return this;
    }

    public Builder forwardSyncMaxBlocksPerMinute(final Integer forwardSyncMaxBlocksPerMinute) {
      checkNotNull(forwardSyncMaxBlocksPerMinute);
      this.forwardSyncMaxBlocksPerMinute = forwardSyncMaxBlocksPerMinute;
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
