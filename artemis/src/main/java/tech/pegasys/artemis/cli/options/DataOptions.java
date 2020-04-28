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

package tech.pegasys.artemis.cli.options;

import picocli.CommandLine.Option;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.StateStorageMode;

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

  public String getDataPath() {
    return dataPath;
  }

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

  private static String defaultDataPath() {
    return VersionProvider.defaultStoragePath() + System.getProperty("file.separator") + "data";
  }
}
