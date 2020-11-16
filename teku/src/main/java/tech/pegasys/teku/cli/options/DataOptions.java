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
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.util.cli.VersionProvider;

public abstract class DataOptions {

  @Option(
      names = {"--data-base-path", "--data-path"},
      paramLabel = "<FILENAME>",
      description = "Path to the base directory for storage",
      arity = "1")
  private Path dataBasePath = defaultDataPath();

  public DataConfig getDataConfig() {
    return configure(DataConfig.builder()).build();
  }

  public void configure(TekuConfiguration.Builder builder) {
    builder.data(this::configure);
  }

  protected DataConfig.Builder configure(final DataConfig.Builder config) {
    return config.dataBasePath(dataBasePath);
  }

  private static Path defaultDataPath() {
    return Path.of(VersionProvider.defaultStoragePath());
  }

  public Path getDataBasePath() {
    return dataBasePath;
  }
}
