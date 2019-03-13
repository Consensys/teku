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

package tech.pegasys.artemis;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.adapter.TimeSeriesAdapter;
import tech.pegasys.artemis.data.provider.CSVProvider;
import tech.pegasys.artemis.data.provider.FileProvider;
import tech.pegasys.artemis.data.provider.JSONProvider;
import tech.pegasys.artemis.data.provider.ProviderTypes;
import tech.pegasys.artemis.networking.p2p.RLPxP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.services.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class BeaconNode {
  private static final ALogger LOG = new ALogger(BeaconNode.class.getName());
  private P2PNetwork p2pNetwork;
  private ValidatorCoordinator validatorCoordinator;
  private EventBus eventBus;
  private String outputFilename;
  private FileProvider<?> fileProvider;

  private CommandLineArguments cliArgs;
  private CommandLine commandLine;

  public BeaconNode(CommandLine commandLine, CommandLineArguments cliArgs) {
    this.eventBus = new AsyncEventBus(Executors.newCachedThreadPool());
    this.p2pNetwork = new RLPxP2PNetwork(eventBus);
    this.validatorCoordinator = new ValidatorCoordinator(eventBus);
    this.cliArgs = cliArgs;
    this.commandLine = commandLine;
    if (cliArgs.isOutputEnabled()) {
      this.eventBus.register(this);
      this.outputFilename = FileProvider.uniqueFilename(cliArgs.getOutputFile());
      if (ProviderTypes.compare(CSVProvider.class, cliArgs.getProviderType())) {
        this.fileProvider = new CSVProvider();
      } else {
        this.fileProvider = new JSONProvider();
      }
    }
  }

  public void start() {
    if (commandLine.isUsageHelpRequested()) {
      commandLine.usage(System.out);
      return;
    }
    // set log level per CLI flags
    System.out.println("Setting logging level to " + cliArgs.getLoggingLevel().name());
    Configurator.setAllLevels("", cliArgs.getLoggingLevel());

    // Check output file

    // Initialize services
    ServiceController.initAll(
        eventBus,
        cliArgs,
        BeaconChainService.class,
        PowchainService.class,
        ChainStorageService.class);
    // Start services
    ServiceController.startAll(cliArgs);
    // Start p2p adapter
    this.p2pNetwork.run();
  }

  public void stop() {
    try {
      ServiceController.stopAll(cliArgs);
      this.p2pNetwork.close();
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }

  @Subscribe
  public void onDataEvent(RawRecord record) {
    TimeSeriesAdapter adapter = new TimeSeriesAdapter(record);
    TimeSeriesRecord tsRecord = adapter.transform();
    fileProvider.setRecord(tsRecord);
    FileProvider.output(outputFilename, fileProvider);
  }
}
