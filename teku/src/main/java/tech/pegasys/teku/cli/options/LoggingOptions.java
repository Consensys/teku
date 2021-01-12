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

import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;

import java.nio.file.Path;
import java.util.Optional;
import picocli.CommandLine;
import tech.pegasys.teku.cli.util.LoggingPathBuilder;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class LoggingOptions {
  @CommandLine.Option(
      names = {"--log-color-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Whether Status and Event log messages include a console color display code",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logColorEnabled = true;

  @CommandLine.Option(
      names = {"--log-include-events-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Whether frequent update events are logged (e.g. every slot and epoch event)",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeEventsEnabled = true;

  @CommandLine.Option(
      names = {"--log-include-validator-duties-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Whether events are logged when validators perform duties",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeValidatorDutiesEnabled = true;

  @CommandLine.Option(
      names = {"--log-destination"},
      paramLabel = "<LOG_DESTINATION>",
      description =
          "Whether a logger is added for the console, the log file, or both (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private LoggingDestination logDestination = DEFAULT_BOTH;

  @CommandLine.Option(
      names = {"--log-file"},
      paramLabel = "<FILENAME>",
      description =
          "Path containing the location (relative or absolute) and the log filename. If not set "
              + "will default to <data-path>/logs/teku.log",
      showDefaultValue = CommandLine.Help.Visibility.NEVER,
      arity = "1")
  private String logFile;

  @CommandLine.Option(
      names = {"--log-file-name-pattern"},
      paramLabel = "<REGEX>",
      // Note: PicoCLI uses String.format to format the description so %% is needed for a single %
      description =
          "Pattern for the filename to apply to rolled over log files. If not set "
              + "will default to <data-path>/logs/teku_%%d{yyyy-MM-dd}.log",
      arity = "1")
  private String logFileNamePattern;

  @CommandLine.Option(
      names = {"--Xlog-wire-cipher-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether raw encrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireCipherEnabled = false;

  @CommandLine.Option(
      names = {"--Xlog-wire-plain-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether raw decrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWirePlainEnabled = false;

  @CommandLine.Option(
      names = {"--Xlog-wire-mux-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether multiplexer wire packets (aka Libp2p stream frames) are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireMuxEnabled = false;

  @CommandLine.Option(
      names = {"--Xlog-wire-gossip-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether gossip messages are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireGossipEnabled = false;

  public boolean isLogColorEnabled() {
    return logColorEnabled;
  }

  public boolean isLogIncludeEventsEnabled() {
    return logIncludeEventsEnabled;
  }

  public boolean isLogIncludeValidatorDutiesEnabled() {
    return logIncludeValidatorDutiesEnabled;
  }

  public LoggingDestination getLogDestination() {
    return logDestination;
  }

  public Optional<String> getMaybeLogFile() {
    return Optional.ofNullable(logFile);
  }

  public Optional<String> getMaybeLogPattern() {
    return Optional.ofNullable(logFileNamePattern);
  }

  public boolean isLogWireCipherEnabled() {
    return logWireCipherEnabled;
  }

  public boolean isLogWirePlainEnabled() {
    return logWirePlainEnabled;
  }

  public boolean isLogWireMuxEnabled() {
    return logWireMuxEnabled;
  }

  public boolean isLogWireGossipEnabled() {
    return logWireGossipEnabled;
  }

  public TekuConfiguration.Builder configure(
      final TekuConfiguration.Builder builder,
      final Path dataBasePath,
      final String defaultLogFile,
      final String defaultLogFileNamePattern) {

    final String logFile =
        new LoggingPathBuilder()
            .defaultBasename(defaultLogFile)
            .dataPath(dataBasePath)
            .maybeFromCommandLine(getMaybeLogFile())
            .build();

    final String logFileNamePattern =
        new LoggingPathBuilder()
            .defaultBasename(defaultLogFileNamePattern)
            .dataPath(dataBasePath)
            .maybeFromCommandLine(getMaybeLogPattern())
            .build();

    return builder.logging(
        loggingBuilder ->
            loggingBuilder
                .colorEnabled(logColorEnabled)
                .includeEventsEnabled(logIncludeEventsEnabled)
                .includeValidatorDutiesEnabled(logIncludeValidatorDutiesEnabled)
                .destination(logDestination)
                .logFile(logFile)
                .logFileNamePattern(logFileNamePattern)
                .logWireCipher(logWireCipherEnabled)
                .logWirePlain(logWirePlainEnabled)
                .logWireMuxFrames(logWireMuxEnabled)
                .logWireGossip(logWireGossipEnabled));
  }
}
