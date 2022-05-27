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

package tech.pegasys.teku.cli.options;

import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.services.chainstorage.StorageConfiguration;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

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

  // data-storage-archive-frequency is hidden, because it has been released
  // and is likely used in nodes  rather than potentially cause issues, hiding the argument
  @CommandLine.Option(
      names = {"--data-storage-archive-frequency"},
      paramLabel = "<FREQUENCY>",
      description =
          "Sets the frequency, in slots, at which to store finalized states to disk. "
              + "This option is ignored if --data-storage-mode is set to PRUNE",
      hidden = true,
      arity = "1")
  private long dataStorageFrequency = VersionedDatabaseFactory.DEFAULT_STORAGE_FREQUENCY;

  @CommandLine.Option(
      names = {"--Xdata-storage-create-db-version"},
      paramLabel = "<VERSION>",
      description = "Database version to create",
      arity = "1",
      hidden = true)
  private String createDbVersion = DatabaseVersion.DEFAULT_VERSION.getValue();

  @CommandLine.Option(
      names = {"--data-storage-non-canonical-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Store non-canonical blocks",
      fallbackValue = "true",
      arity = "0..1")
  private boolean storeNonCanonicalBlocksEnabled =
      StorageConfiguration.DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;

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

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

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
                .maxKnownNodeCacheSize(maxKnownNodeCacheSize));
  }

  private DatabaseVersion parseDatabaseVersion() {
    return DatabaseVersion.fromString(createDbVersion).orElse(DatabaseVersion.DEFAULT_VERSION);
  }

  public boolean isStoreNonCanonicalBlocks() {
    return storeNonCanonicalBlocksEnabled;
  }
}
