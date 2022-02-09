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

import picocli.CommandLine;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class NodeLoggingOptions extends LoggingOptions {
  @CommandLine.Option(
      names = {"--log-destination"},
      paramLabel = "<LOG_DESTINATION>",
      description =
          "Whether a logger is added for the console, the log file, or both (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private LoggingDestination logDestination = DEFAULT_BOTH;

  @Option(
      names = {"--Xlog-wire-cipher-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether raw encrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireCipherEnabled = false;

  @Option(
      names = {"--Xlog-wire-plain-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether raw decrypted wire packets are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWirePlainEnabled = false;

  @Option(
      names = {"--Xlog-wire-mux-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether multiplexer wire packets (aka Libp2p stream frames) are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireMuxEnabled = false;

  @Option(
      names = {"--Xlog-wire-gossip-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description = "Whether gossip messages are logged",
      fallbackValue = "true",
      arity = "0..1")
  private boolean logWireGossipEnabled = false;

  public TekuConfiguration.Builder configureWireLogs(final TekuConfiguration.Builder builder) {
    return builder.wireLogs(
        b ->
            b.logWireCipher(logWireCipherEnabled)
                .logWirePlain(logWirePlainEnabled)
                .logWireMuxFrames(logWireMuxEnabled)
                .logWireGossip(logWireGossipEnabled));
  }

  public LoggingConfig applyLoggingConfiguration(
      final String dataDirectory, final String defaultLogFileNamePrefix) {
    return applyLoggingConfiguration(dataDirectory, defaultLogFileNamePrefix, logDestination);
  }
}
