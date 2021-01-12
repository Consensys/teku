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

package tech.pegasys.teku;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.vertx.core.Vertx;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.ValidatorNodeServiceController;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.Constants;

public class ValidatorNode implements Node {

  private final Vertx vertx = Vertx.vertx();
  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("events-%d").build());

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final ValidatorNodeServiceController serviceController;
  private final EventChannels eventChannels;
  private final MetricsEndpoint metricsEndpoint;

  public ValidatorNode(final TekuConfiguration tekuConfig) {
    LoggingConfigurator.update(tekuConfig.loggingConfig());

    STATUS_LOG.onStartup(VersionProvider.VERSION);
    this.metricsEndpoint = new MetricsEndpoint(tekuConfig.metricsConfig(), vertx);
    final MetricsSystem metricsSystem = metricsEndpoint.getMetricsSystem();
    final TekuDefaultExceptionHandler subscriberExceptionHandler =
        new TekuDefaultExceptionHandler();
    this.eventChannels = new EventChannels(subscriberExceptionHandler, metricsSystem);
    final EventBus eventBus = new AsyncEventBus(threadPool, subscriberExceptionHandler);

    asyncRunnerFactory = new AsyncRunnerFactory(new MetricTrackingExecutorFactory(metricsSystem));
    final ServiceConfig serviceConfig =
        new ServiceConfig(
            asyncRunnerFactory,
            new SystemTimeProvider(),
            eventBus,
            eventChannels,
            metricsSystem,
            DataDirLayout.createFrom(tekuConfig.dataConfig()));
    Constants.setConstants(tekuConfig.eth2NetworkConfiguration().getConstants());

    this.serviceController = new ValidatorNodeServiceController(tekuConfig, serviceConfig);
    STATUS_LOG.validatorDataPathSet(serviceConfig.getDataDirLayout().getValidatorDataDirectory());
  }

  @Override
  public void start() {
    metricsEndpoint.start().join();
    serviceController.start().join();
  }

  @Override
  public void stop() {
    // Stop processing new events
    eventChannels.stop();
    threadPool.shutdownNow();

    // Stop async actions
    asyncRunnerFactory.getAsyncRunners().forEach(AsyncRunner::shutdown);

    // Stop services. This includes closing the database.
    serviceController.stop().reportExceptions();
    SafeFuture.of(metricsEndpoint.stop()).reportExceptions();
    vertx.close();
  }
}
