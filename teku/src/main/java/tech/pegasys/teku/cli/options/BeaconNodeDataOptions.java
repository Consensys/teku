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

package tech.pegasys.teku.cli.options;

import java.nio.file.Path;
import java.time.Duration;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;

public class BeaconNodeDataOptions extends ValidatorClientDataOptions {

  @Option(
      names = {"--data-beacon-path"},
      paramLabel = "<FILENAME>",
      description = "Path to beacon node data\n  Default: <data-base-path>/beacon",
      arity = "1")
  private Path dataBeaconPath;

  @CommandLine.Option(
      names = {"--data-storage-mode"},
      paramLabel = "<STORAGE_MODE>",
      description =
          "Sets the strategy for handling historical chain data.  (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;

  @CommandLine.Option(
      names = {"--data-storage-archive-frequency"},
      paramLabel = "<FREQUENCY>",
      description =
          "Sets the frequency, in slots, at which to store finalized states to disk. "
              + "This option is ignored if --data-storage-mode is set to PRUNE",
      arity = "1")
  private long dataStorageFrequency = StorageConfiguration.DEFAULT_STORAGE_FREQUENCY;

  @CommandLine.Option(
      names = {"--Xdata-storage-create-db-version"},
      paramLabel = "<VERSION>",
      description = "Database version to create",
      arity = "1",
      hidden = true)
  private String createDbVersion = null;

  @CommandLine.Option(
      names = {"--data-storage-non-canonical-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Store non-canonical blocks",
      fallbackValue = "true",
      arity = "0..1")
  private boolean storeNonCanonicalBlocksEnabled =
      StorageConfiguration.DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;

  @CommandLine.Option(
      names = {"--Xdata-storage-store-payload-separately-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Store the execution payload of blocks separate to the rest of the block",
      fallbackValue = "true",
      hidden = true,
      arity = "0..1")
  private boolean storeBlockExecutionPayloadSeparately =
      StorageConfiguration.DEFAULT_STORE_BLOCK_PAYLOAD_SEPARATELY;

  @CommandLine.Option(
      names = {"--Xdata-storage-block-migrate-batch-size"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Set the batch size for migrating finalized blocks to blinded storage",
      hidden = true,
      arity = "1")
  private int blockMigrationBatchSize = StorageConfiguration.DEFAULT_BLOCK_MIGRATION_BATCH_SIZE;

  @CommandLine.Option(
      names = {"--Xdata-storage-block-migrate-batch-delay-ms"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Set the delay time in millis to wait after committing a batch of blocks",
      hidden = true,
      arity = "1")
  private int blockMigrationBatchDelayMillis =
      StorageConfiguration.DEFAULT_BLOCK_MIGRATION_BATCH_DELAY_MS;

  /**
   * Default value selected based on experimentation to minimise memory usage without affecting sync
   * time. Not that states later in the chain with more validators have more branches so need a
   * bigger cache. We may periodically need to review this setting but it shouldn't need to change
   * often.
   */
  @CommandLine.Option(
      names = {"--Xdata-storage-max-known-node-cache-size"},
      paramLabel = "<INTEGER>",
      description = "Maximum size of the in-memory known node cache for finalized states",
      arity = "1",
      hidden = true)
  private int maxKnownNodeCacheSize = StorageConfiguration.DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;

  @CommandLine.Option(
      names = {"--Xreconstruct-historic-states"},
      paramLabel = "<BOOLEAN>",
      description = "",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private Boolean reconstructHistoricStates =
      SyncConfig.DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED;

  @CommandLine.Option(
      names = {"--Xdata-storage-block-pruning-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Prune finalized blocks prior to the required retention period",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private boolean blockPruningEnabled = StorageConfiguration.DEFAULT_BLOCK_PRUNING_ENABLED;

  @CommandLine.Option(
      names = {"--Xdata-storage-block-pruning-interval"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Interval in seconds between finalized block pruning",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long blockPruningIntervalSeconds =
      StorageConfiguration.DEFAULT_BLOCK_PRUNING_INTERVAL.toSeconds();

  @Override
  protected DataConfig.Builder configureDataConfig(final DataConfig.Builder config) {
    return super.configureDataConfig(config).beaconDataPath(dataBeaconPath);
  }

  @Override
  public void configure(final TekuConfiguration.Builder builder) {
    super.configure(builder);
    builder.storageConfiguration(
        b ->
            b.dataStorageMode(dataStorageMode)
                .dataStorageFrequency(dataStorageFrequency)
                .dataStorageCreateDbVersion(parseDatabaseVersion())
                .storeNonCanonicalBlocks(storeNonCanonicalBlocksEnabled)
                .storeBlockExecutionPayloadSeparately(storeBlockExecutionPayloadSeparately)
                .blockMigrationBatchSize(blockMigrationBatchSize)
                .blockMigrationBatchDelay(blockMigrationBatchDelayMillis)
                .maxKnownNodeCacheSize(maxKnownNodeCacheSize)
                .blockPruningEnabled(blockPruningEnabled)
                .blockPruningInterval(Duration.ofSeconds(blockPruningIntervalSeconds)));
    builder.sync(
        b ->
            b.fetchAllHistoricBlocks(!blockPruningEnabled)
                .reconstructHistoricStatesEnabled(reconstructHistoricStates));
  }

  private DatabaseVersion parseDatabaseVersion() {
    if (createDbVersion == null) {
      if (dataStorageFrequency == 1 && !DatabaseVersion.isLevelDbSupported()) {
        throw new InvalidConfigurationException(
            "Native LevelDB support is required for archive frequency 1");
      }
      return dataStorageFrequency == 1
          ? DatabaseVersion.LEVELDB_TREE
          : DatabaseVersion.DEFAULT_VERSION;
    }
    return DatabaseVersion.fromString(createDbVersion).orElse(DatabaseVersion.DEFAULT_VERSION);
  }

  public boolean isStoreBlockExecutionPayloadSeparately() {
    return storeBlockExecutionPayloadSeparately;
  }
}
