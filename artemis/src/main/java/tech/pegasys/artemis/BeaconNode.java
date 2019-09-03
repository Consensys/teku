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
import io.vertx.core.Vertx;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import tech.pegasys.artemis.data.provider.CSVProvider;
import tech.pegasys.artemis.data.provider.EventHandler;
import tech.pegasys.artemis.data.provider.FileProvider;
import tech.pegasys.artemis.data.provider.JSONProvider;
import tech.pegasys.artemis.data.provider.ProviderTypes;
import tech.pegasys.artemis.data.provider.RawRecordHandler;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.metrics.MetricsEndpoint;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconNode {
  private static final ALogger LOG = new ALogger(BeaconNode.class.getName());
  private final Vertx vertx = Vertx.vertx();
  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
          });

  private final ServiceController serviceController = new ServiceController();
  private final ServiceConfig serviceConfig;
  private Constants constants;
  private EventBus eventBus;
  private FileProvider fileProvider;
  private EventHandler eventHandler;
  private MetricsEndpoint metricsEndpoint;

  private CommandLineArguments cliArgs;
  private CommandLine commandLine;

  public BeaconNode(CommandLine commandLine, CommandLineArguments cliArgs) {
    this(commandLine, cliArgs, ArtemisConfiguration.fromFile(cliArgs.getConfigFile()));
  }

  BeaconNode(CommandLine commandLine, CommandLineArguments cliArgs, ArtemisConfiguration config) {
    System.setProperty("logPath", config.getLogPath());
    System.setProperty("rollingFile", config.getLogFile());

    this.eventBus = new AsyncEventBus(threadPool);

    metricsEndpoint = new MetricsEndpoint(config, vertx);
    this.serviceConfig =
        new ServiceConfig(eventBus, vertx, metricsEndpoint.getMetricsSystem(), config, cliArgs);
    Constants.init(config);
    this.cliArgs = cliArgs;
    this.commandLine = commandLine;

    // register a raw record handler that will transform objects to events
    new RawRecordHandler(this.eventBus);

    if (config.isOutputEnabled()) {
      this.eventBus.register(this);
      try {
        Path outputFilename = FileProvider.uniqueFilename(config.getOutputFile());
        if (ProviderTypes.compare(CSVProvider.class, config.getProviderType())) {
          this.fileProvider = new CSVProvider(outputFilename);
        } else if (ProviderTypes.compare(JSONProvider.class, config.getProviderType())) {
          this.fileProvider = new JSONProvider(outputFilename);
        } else {
          throw new UnsupportedOperationException(
              "Provider not supported " + config.getProviderType());
        }
        this.eventHandler = new EventHandler(config, fileProvider);
        this.eventBus.register(eventHandler);
      } catch (IOException e) {
        LOG.log(Level.ERROR, e.getMessage());
      }
    }

    // set log level per CLI flags
    System.out.println("Setting logging level to " + cliArgs.getLoggingLevel().name());
    Configurator.setAllLevels("", cliArgs.getLoggingLevel());
  }

  public void start() {

    try {
      metricsEndpoint.start();
      // Initialize services
      serviceController.initAll(
          serviceConfig,
          BeaconChainService.class,
          PowchainService.class,
          ChainStorageService.class);

      // Start services
      serviceController.startAll(cliArgs);

    } catch (java.util.concurrent.CompletionException e) {
      LOG.log(Level.FATAL, e.toString());
    }
  }

  public void stop() {
    serviceController.stopAll(cliArgs);
    metricsEndpoint.stop();
    this.fileProvider.close();
  }
}
