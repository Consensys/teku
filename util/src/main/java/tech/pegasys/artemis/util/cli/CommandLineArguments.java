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

import java.util.List;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
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
      arity = "0",
      paramLabel = "<FILENAME>",
      description = "PoW simulation flag, w/ optional input file")
  private String inputFile = null;

  @Option(
      names = {"-f", "--format"},
      paramLabel = "<IS FORMAT>",
      description = "Output of JSON file is serial or formatted")
  private boolean isFormat = false;

  // Specify events that will be output for logging
  @CommandLine.Parameters(
      paramLabel = "<EVENT>",
      description = "Output selector for specific events")
  private List<String> events;

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
    return !(inputFile == null);
  }

  public String getInputFile() {
    if (inputFile == null || inputFile.equals("")) return null;
    return inputFile;
  }

  public boolean isFormat() {
    return isFormat;
  }

  public List<String> getEvents() {
    return events;
  }
}
