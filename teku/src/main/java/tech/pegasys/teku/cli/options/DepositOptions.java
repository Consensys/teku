/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;

public class DepositOptions {

  @Option(
      names = {"--eth1-endpoints", "--eth1-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URLs for Eth1 nodes.",
      split = ",",
      arity = "1..*")
  private List<String> eth1Endpoints = new ArrayList<>();

  @Option(
      names = {"--eth1-deposit-contract-max-request-size"},
      paramLabel = "<INTEGER>",
      description =
          "Maximum number of blocks to request deposit contract event logs for in a single request.",
      arity = "1")
  private int eth1LogsMaxBlockRange = PowchainConfiguration.DEFAULT_ETH1_LOGS_MAX_BLOCK_RANGE;

  @Option(
      names = {"--Xeth1-missing-deposits-event-logging-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable logging an event on each slot whenever deposits are missing",
      hidden = true,
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean useMissingDepositEventLogging =
      PowchainConfiguration.DEFAULT_USE_MISSING_DEPOSIT_EVENT_LOGGING;

  @CommandLine.Option(
      names = {"--Xdeposit-snapshot"},
      paramLabel = "<STRING>",
      description =
          "Deposit tree snapshot. This value should be a file or URL pointing to a SSZ-encoded finalized deposit tree snapshot.",
      hidden = true,
      arity = "1")
  private String depositSnapshot;

  @Option(
      names = {"--Xdeposit-snapshot-storage-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable loading of finalized deposit tree snapshot from storage",
      hidden = true,
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean depositSnapshotStorageEnabled =
      PowchainConfiguration.DEFAULT_DEPOSIT_SNAPSHOT_STORAGE_ENABLED;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.powchain(
        b ->
            b.eth1Endpoints(eth1Endpoints)
                .eth1LogsMaxBlockRange(eth1LogsMaxBlockRange)
                .useMissingDepositEventLogging(useMissingDepositEventLogging)
                .depositSnapshot(depositSnapshot)
                .depositSnapshotStorageEnabled(depositSnapshotStorageEnabled));
  }
}
