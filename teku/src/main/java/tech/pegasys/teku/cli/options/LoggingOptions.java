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

import org.apache.logging.log4j.Level;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.LogTypeConverter;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class LoggingOptions {
  private static final String WINDOWS_SEP = "\\";
  private static final String LINUX_SEP = "/";

  @Option(
      names = {"-l", "--logging"},
      converter = LogTypeConverter.class,
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).",
      arity = "1")
  private Level logLevel;

  @Option(
      names = {"--log-color-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Whether Status and Event log messages include a console color display code",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logColorEnabled = true;

  @Option(
      names = {"--log-include-events-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Whether frequent update events are logged (e.g. every slot and epoch event)",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeEventsEnabled = true;

  @Option(
      names = {"--log-include-validator-duties-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Whether events are logged when validators perform duties",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeValidatorDutiesEnabled = true;

  @Option(
      names = {"--Xlog-include-p2p-warnings-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Whether warnings are logged for invalid P2P messages",
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeP2pWarningsEnabled = false;

  @Option(
      names = {"--log-file"},
      paramLabel = "<FILENAME>",
      description =
          "Path containing the location (relative or absolute) and the log filename. If not set "
              + "will default to <data-path>/logs/teku.log",
      showDefaultValue = Visibility.NEVER,
      arity = "1")
  private String logFile;

  @Option(
      names = {"--log-file-name-pattern"},
      paramLabel = "<REGEX>",
      // Note: PicoCLI uses String.format to format the description so %% is needed for a single %
      description =
          "Pattern for the filename to apply to rolled over log files. If not set "
              + "will default to <data-path>/logs/teku_%%d{yyyy-MM-dd}.log",
      arity = "1")
  private String logFileNamePattern;

  private boolean containsPath(String file) {
    return file.contains(LINUX_SEP) || file.contains(WINDOWS_SEP);
  }

  public LoggingConfig applyLoggingConfiguration(
      final String dataDirectory,
      final String defaultLogFileNamePrefix,
      final LoggingDestination logDestination) {
    final LoggingConfig.LoggingConfigBuilder loggingBuilder = LoggingConfig.builder();
    loggingBuilder.logFileNamePrefix(defaultLogFileNamePrefix);
    loggingBuilder.dataDirectory(dataDirectory);
    if (logFile != null) {
      if (containsPath(logFile)) {
        loggingBuilder.logPath(logFile);
      } else {
        loggingBuilder.logFileName(logFile);
      }
    }
    if (logFileNamePattern != null) {
      if (containsPath(logFileNamePattern)) {
        loggingBuilder.logPathPattern(logFileNamePattern);
      } else {
        loggingBuilder.logFileNamePattern(logFileNamePattern);
      }
    }
    loggingBuilder
        .logLevel(logLevel)
        .colorEnabled(logColorEnabled)
        .includeEventsEnabled(logIncludeEventsEnabled)
        .includeValidatorDutiesEnabled(logIncludeValidatorDutiesEnabled)
        .includeP2pWarningsEnabled(logIncludeP2pWarningsEnabled)
        .destination(logDestination);
    return loggingBuilder.build();
  }
}
