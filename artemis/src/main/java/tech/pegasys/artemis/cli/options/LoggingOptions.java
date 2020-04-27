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

  public static final String LOG_COLOR_ENABLED_OPTION_NAME = "--log-color-enabled";
  public static final String LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME =
      "--log-include-events-enabled";
  public static final String LOG_DESTINATION_OPTION_NAME = "--log-destination";
  public static final String LOG_FILE_OPTION_NAME = "--log-file";
  public static final String LOG_FILE_NAME_PATTERN_OPTION_NAME = "--log-file-name-pattern";
  public static final String LOG_WIRE_CIPHER_ENABLED_OPTION_NAME = "--log-wire-cipher-enabled";
  public static final String LOG_WIRE_PLAIN_ENABLED_OPTION_NAME = "--log-wire-plain-enabled";
  public static final String LOG_WIRE_MUX_ENABLED_OPTION_NAME = "--log-wire-mux-enabled";
  public static final String LOG_WIRE_GOSSIP_ENABLED_OPTION_NAME = "--log-wire-gossip-enabled";

  public static final boolean DEFAULT_LOG_COLOR_ENABLED = true;
  public static final boolean DEFAULT_LOG_INCLUDE_EVENTS_ENABLED = true;
  public static final LoggingDestination DEFAULT_LOG_DESTINATION = DEFAULT_BOTH;
  private static final String SEP = System.getProperty("file.separator");
  public static final String DEFAULT_LOG_FILE =
      StringUtils.joinWith(SEP, VersionProvider.defaultStoragePath(), "logs", "teku.log");
  public static final String DEFAULT_LOG_FILE_NAME_PATTERN =
      StringUtils.joinWith(
          SEP, VersionProvider.defaultStoragePath(), "logs", "teku_%d{yyyy-MM-dd}.log");
  public static final boolean DEFAULT_LOG_WIRE_CIPHER_ENABLED = false;
  public static final boolean DEFAULT_LOG_WIRE_PLAIN_ENABLED = false;
  public static final boolean DEFAULT_LOG_WIRE_MUX_ENABLED = false;
  public static final boolean DEFAULT_LOG_WIRE_GOSSIP_ENABLED = false;

  @CommandLine.Option(
      names = {LOG_COLOR_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether Status and Event log messages include a console color display code",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logColorEnabled = DEFAULT_LOG_COLOR_ENABLED;

  @CommandLine.Option(
      names = {LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description =
          "Whether frequent update events are logged (e.g. every slot event, with validators and attestations)",
      arity = "1")
  private boolean logIncludeEventsEnabled = DEFAULT_LOG_INCLUDE_EVENTS_ENABLED;

  @CommandLine.Option(
      names = {LOG_DESTINATION_OPTION_NAME},
      paramLabel = "<LOG_DESTINATION>",
      description =
          "Whether a logger is added for the console, the log file, or both (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private LoggingDestination logDestination = DEFAULT_LOG_DESTINATION;

  @CommandLine.Option(
      names = {LOG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path containing the location (relative or absolute) and the log filename.",
      arity = "1")
  private String logFile = DEFAULT_LOG_FILE;

  @CommandLine.Option(
      names = {LOG_FILE_NAME_PATTERN_OPTION_NAME},
      paramLabel = "<REGEX>",
      description = "Pattern for the filename to apply to rolled over log files.",
      arity = "1")
  private String logFileNamePattern = DEFAULT_LOG_FILE_NAME_PATTERN;

  @CommandLine.Option(
      names = {LOG_WIRE_CIPHER_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether raw encrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireCipherEnabled = DEFAULT_LOG_WIRE_CIPHER_ENABLED;

  @CommandLine.Option(
      names = {LOG_WIRE_PLAIN_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether raw decrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWirePlainEnabled = DEFAULT_LOG_WIRE_PLAIN_ENABLED;

  @CommandLine.Option(
      names = {LOG_WIRE_MUX_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether multiplexer wire packets (aka Libp2p stream frames) are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireMuxEnabled = DEFAULT_LOG_WIRE_MUX_ENABLED;

  @CommandLine.Option(
      names = {LOG_WIRE_GOSSIP_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether gossip messages are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireGossipEnabled = DEFAULT_LOG_WIRE_GOSSIP_ENABLED;

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
}
