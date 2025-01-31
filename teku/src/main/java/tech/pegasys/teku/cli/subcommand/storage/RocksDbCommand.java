/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.cli.subcommand.storage;

import java.util.ArrayList;
import java.util.List;
import org.rocksdb.RocksDBException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbHelper;


@Command(
    name = "rocksdb",
    description = "Print RocksDB information",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class)
public class RocksDbCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @Command(
      name = "usage",
      description = "Print disk usage",
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class)
  public int usage(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions) {

    RocksDbHelper.printTableHeader(SUB_COMMAND_LOG);
    final String dbPath =
        DataDirLayout.createFrom(beaconNodeDataOptions.getDataConfig()).getBeaconDataDirectory()
            + "/db";
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();
    final List<RocksDbHelper.ColumnFamilyUsage> columnFamilyUsages = new ArrayList<>();
    RocksDbHelper.forEachColumnFamily(
        dbPath,
        networkConfiguration,
        (rocksdb, cfHandle, spec) -> {
          try {
            columnFamilyUsages.add(
                RocksDbHelper.getAndPrintUsageForColumnFamily(
                    rocksdb, cfHandle, spec, SUB_COMMAND_LOG));
          } catch (RocksDBException e) {
            throw new RuntimeException(e);
          }
        });
    RocksDbHelper.printTotals(SUB_COMMAND_LOG, columnFamilyUsages);
    return 0;
  }

  @Command(
      name = "stats",
      description = "Print rocksdb stats",
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class)
  public int stats(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions) {

    final String dbPath =
        DataDirLayout.createFrom(beaconNodeDataOptions.getDataConfig()).getBeaconDataDirectory()
            + "/db";
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();
    SUB_COMMAND_LOG.display("Column Family Stats...");
    RocksDbHelper.forEachColumnFamily(
        dbPath,
        networkConfiguration,
        (rocksdb, cfHandle, spec) -> {
          try {
            RocksDbHelper.printStatsForColumnFamily(rocksdb, cfHandle, spec, SUB_COMMAND_LOG);
          } catch (RocksDBException e) {
            throw new RuntimeException(e);
          }
        });
    return 0;
  }
}
