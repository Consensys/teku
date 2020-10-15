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
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;

public class BeaconNodeDataOptions extends ValidatorClientDataOptions {

  @Option(
      names = {"--data-beacon-path"},
      paramLabel = "<FILENAME>",
      description = "Path to beacon node data\n  Default: <data-base-path>/beacon",
      arity = "1")
  private Path dataBeaconPath;

  @Override
  protected DataConfig.Builder configure(final DataConfig.Builder config) {
    return super.configure(config).beaconDataPath(dataBeaconPath);
  }
}
