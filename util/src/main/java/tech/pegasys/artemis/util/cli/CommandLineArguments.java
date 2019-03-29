/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.cli;

import org.apache.logging.log4j.Level;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "Artemis", mixinStandardHelpOptions = true)
public class CommandLineArguments {
  @Option(
      names = {"-p", "--provider"},
      paramLabel = "<PROVIDER TYPE>",
      description = "Output provider types: CSV, JSON (default: JSON).")
  private String providerType = "JSON";

  @Option(
      names = {"-o", "--output"},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the output file")
  private String outputFile = "";

  @Option(
      names = {"-l", "--logging"},
      converter = LogTypeConverter.class,
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).")
  private Level logLevel = Level.INFO;

  @Option(
      names = {"-c", "--config"},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the config file")
  private String configFile = "./config/config.toml";

  @Option(
      names = {"-s", "--sim"},
      description = "PoW simulation flag")
  private boolean simulation = false;

  public String getProviderType() {
    return this.providerType;
  }

  public String getOutputFile() {
    return this.outputFile;
  }

  public Boolean isOutputEnabled() {
    return this.outputFile.length() > 0;
  }

  public Level getLoggingLevel() {
    return this.logLevel;
  }

  public String getConfigFile() {
    return configFile;
  }

  public boolean isSimulation() {
    return simulation;
  }
}
