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
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import tech.pegasys.artemis.data.recorder.SSZTransitionRecorder;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.metrics.MetricsEndpoint;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.alogger.ALogger;
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
  private EventBus eventBus;
  private MetricsEndpoint metricsEndpoint;

  BeaconNode(Level loggingLevel, ArtemisConfiguration config) {
    System.setProperty("logPath", config.getLogPath());
    System.setProperty("rollingFile", config.getLogFile());

    this.eventBus = new AsyncEventBus(threadPool);

    metricsEndpoint = new MetricsEndpoint(config, vertx);
    this.serviceConfig =
        new ServiceConfig(eventBus, vertx, metricsEndpoint.getMetricsSystem(), config);
    Constants.setConstants(config.getConstants());

    final String transitionRecordDir = config.getTransitionRecordDir();
    if (transitionRecordDir != null) {
      eventBus.register(new SSZTransitionRecorder(Path.of(transitionRecordDir)));
    }

    // set log level per CLI flags
    System.out.println("Setting logging level to " + loggingLevel.name());
    Configurator.setAllLevels("", loggingLevel);
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
      serviceController.startAll();

    } catch (java.util.concurrent.CompletionException e) {
      LOG.log(Level.FATAL, e.toString());
    }
  }

  public void stop() {
    serviceController.stopAll();
    metricsEndpoint.stop();
  }
}
