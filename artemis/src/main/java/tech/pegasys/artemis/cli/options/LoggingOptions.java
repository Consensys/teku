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

import static tech.pegasys.artemis.util.config.LoggingDestination.DEFAULT_BOTH;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.LoggingDestination;

public class LoggingOptions {

  private static final String SEP = System.getProperty("file.separator");
  public static final String DEFAULT_LOG_FILE =
      StringUtils.joinWith(SEP, VersionProvider.defaultStoragePath(), "logs", "teku.log");
  public static final String DEFAULT_LOG_FILE_NAME_PATTERN =
      StringUtils.joinWith(
          SEP, VersionProvider.defaultStoragePath(), "logs", "teku_%d{yyyy-MM-dd}.log");

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
      description =
          "Whether frequent update events are logged (e.g. every slot event, with validators and attestations)",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logIncludeEventsEnabled = true;

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
      description = "Path containing the location (relative or absolute) and the log filename.",
      arity = "1")
  private String logFile = DEFAULT_LOG_FILE;

  @CommandLine.Option(
      names = {"--log-file-name-pattern"},
      paramLabel = "<REGEX>",
      description = "Pattern for the filename to apply to rolled over log files.",
      arity = "1")
  private String logFileNamePattern = DEFAULT_LOG_FILE_NAME_PATTERN;

  public boolean isLogColorEnabled() {
    return logColorEnabled;
  }

  public boolean isLogIncludeEventsEnabled() {
    return logIncludeEventsEnabled;
  }

  public LoggingDestination getLogDestination() {
    return logDestination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }
}
