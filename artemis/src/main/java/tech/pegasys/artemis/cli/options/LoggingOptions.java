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

import picocli.CommandLine;

public class LoggingOptions {

  public static final String LOG_COLOUR_ENABLED_OPTION_NAME = "--log-colour-enabled";
  public static final String LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME =
      "--log-include-events-enabled";
  public static final String LOG_DESTINATION_OPTION_NAME = "--log-destination";
  public static final String LOG_FILE_OPTION_NAME = "--log-file";
  public static final String LOG_FILE_NAME_PATTERN_OPTION_NAME = "--log-file-name-pattern";

  public static final boolean DEFAULT_LOG_COLOUR_ENABLED = true;
  public static final boolean DEFAULT_LOG_INCLUDE_EVENTS_ENABLED = true;
  public static final String DEFAULT_LOG_DESTINATION = "both";
  public static final String DEFAULT_LOG_FILE = "teku.log";
  public static final String DEFAULT_LOG_FILE_NAME_PATTERN = "teku_%d{yyyy-MM-dd}.log";

  @CommandLine.Option(
      names = {LOG_COLOUR_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether Status and Event log messages include a console color display code",
      arity = "1")
  private boolean logColourEnabled = DEFAULT_LOG_COLOUR_ENABLED;

  @CommandLine.Option(
      names = {LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description =
          "Whether the frequent update events are logged (e.g. every slot event, with validators and attestations))",
      arity = "1")
  private boolean logIncludeEventsEnabled = DEFAULT_LOG_INCLUDE_EVENTS_ENABLED;

  @CommandLine.Option(
      names = {LOG_DESTINATION_OPTION_NAME},
      paramLabel = "<LOG_DESTINATION>",
      description = "Whether all logs go only to the console, only to the log file, or both",
      arity = "1")
  private String logDestination = DEFAULT_LOG_DESTINATION;

  @CommandLine.Option(
      names = {LOG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path containing the location (relative or absolute) and the log filename.",
      arity = "1")
  private String logFile = DEFAULT_LOG_FILE;

  @CommandLine.Option(
      names = {LOG_FILE_NAME_PATTERN_OPTION_NAME},
      paramLabel = "<REGEX>",
      description = "Pattern for the filename to apply to rolled over logs files.",
      arity = "1")
  private String logFileNamePattern = DEFAULT_LOG_FILE_NAME_PATTERN;

  public boolean isLogColourEnabled() {
    return logColourEnabled;
  }

  public boolean isLogIncludeEventsEnabled() {
    return logIncludeEventsEnabled;
  }

  public String getLogDestination() {
    return logDestination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }
}
