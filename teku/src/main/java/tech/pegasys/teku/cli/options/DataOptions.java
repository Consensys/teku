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

import picocli.CommandLine.Option;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.StateStorageMode;

public class DataOptions {

  @Option(
      names = {"--data-path"},
      paramLabel = "<FILENAME>",
      description = "Path to output data files",
      arity = "1")
  private String dataPath = defaultDataPath();

  @Option(
      names = {"--data-storage-mode"},
      paramLabel = "<STORAGE_MODE>",
      description =
          "Sets the strategy for handling historical chain data.  (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private StateStorageMode dataStorageMode = StateStorageMode.PRUNE;

  @Option(
      names = {"--data-storage-archive-frequency"},
      hidden = true,
      paramLabel = "<FREQUENCY>",
      description = "Sets the frequency, in slots, at which to store archived states to disk.",
      arity = "1")
  private long dataStorageFrequency = 2048L;

  @Option(
      names = {"--Xdata-storage-create-db-version"},
      paramLabel = "<VERSION>",
      description = "Database version to create (3.0 or 4)",
      arity = "1",
      hidden = true)
  private String createDbVersion = DatabaseVersion.DEFAULT_VERSION.getValue();

  public String getDataPath() {
    return dataPath;
  }

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

  public long getDataStorageFrequency() {
    return dataStorageFrequency;
  }

  public String getCreateDbVersion() {
    return createDbVersion;
  }

  private static String defaultDataPath() {
    return VersionProvider.defaultStoragePath() + System.getProperty("file.separator") + "data";
  }
}
