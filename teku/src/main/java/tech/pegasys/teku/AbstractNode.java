/*
 * Copyright 2021 ConsenSys AG.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.data.publisher.MetricsPublisherManager;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.OccuranceCounter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.ServiceController;
import tech.pegasys.teku.spec.SpecMilestone;

public abstract class AbstractNode implements Node {
  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx = Vertx.vertx();
  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("events-%d").build());

  private final OccuranceCounter rejectedExecutionCounter = new OccuranceCounter(120);

  private Optional<Cancellable> counterMaintainer = Optional.empty();

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final EventChannels eventChannels;
  private final MetricsEndpoint metricsEndpoint;
  private final MetricsPublisherManager metricsPublisher;
  protected final ServiceConfig serviceConfig;

  protected AbstractNode(final TekuConfiguration tekuConfig) {
    STATUS_LOG.onStartup(VersionProvider.VERSION);
    reportOverrides(tekuConfig);
    this.metricsEndpoint = new MetricsEndpoint(tekuConfig.metricsConfig(), vertx);
    final MetricsSystem metricsSystem = metricsEndpoint.getMetricsSystem();
    final TekuDefaultExceptionHandler subscriberExceptionHandler =
        new TekuDefaultExceptionHandler();
    this.eventChannels = new EventChannels(subscriberExceptionHandler, metricsSystem);

    asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(metricsSystem, rejectedExecutionCounter));
    final DataDirLayout dataDirLayout = DataDirLayout.createFrom(tekuConfig.dataConfig());
    serviceConfig =
        new ServiceConfig(
            asyncRunnerFactory,
            new SystemTimeProvider(),
            eventChannels,
            metricsSystem,
            dataDirLayout,
            rejectedExecutionCounter::getTotalCount);
    this.metricsPublisher =
        new MetricsPublisherManager(
            asyncRunnerFactory,
            serviceConfig.getTimeProvider(),
            metricsEndpoint,
            dataDirLayout.getBeaconDataDirectory().toFile());
  }

  private void reportOverrides(final TekuConfiguration tekuConfig) {
    tekuConfig
        .eth2NetworkConfiguration()
        .getAltairForkEpoch()
        .ifPresent(
            forkEpoch -> STATUS_LOG.warnForkEpochChanged(SpecMilestone.ALTAIR.name(), forkEpoch));

    tekuConfig
        .eth2NetworkConfiguration()
        .getBellatrixForkEpoch()
        .ifPresent(
            forkEpoch ->
                STATUS_LOG.warnForkEpochChanged(SpecMilestone.BELLATRIX.name(), forkEpoch));

    tekuConfig
        .eth2NetworkConfiguration()
        .getTotalTerminalDifficultyOverride()
        .ifPresent(
            ttdo ->
                STATUS_LOG.warnBellatrixParameterChanged(
                    "TERMINAL_TOTAL_DIFFICULTY", ttdo.toString()));

    tekuConfig
        .eth2NetworkConfiguration()
        .getTerminalBlockHashOverride()
        .ifPresent(
            tbho ->
                STATUS_LOG.warnBellatrixParameterChanged("TERMINAL_BLOCK_HASH", tbho.toString()));

    tekuConfig
        .eth2NetworkConfiguration()
        .getTerminalBlockHashEpochOverride()
        .ifPresent(
            tbheo ->
                STATUS_LOG.warnBellatrixParameterChanged(
                    "TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH", tbheo.toString()));
  }

  @Override
  public abstract ServiceController getServiceController();

  @Override
  public void start() {
    metricsEndpoint.start().join();
    metricsPublisher.start().join();
    getServiceController().start().join();
    counterMaintainer =
        Optional.of(
            serviceConfig
                .createAsyncRunner("RejectedExecutionCounter", 1)
                .runWithFixedDelay(
                    this::pollRejectedExecutions,
                    Duration.ofSeconds(5),
                    (err) -> LOG.debug("rejected execution poll failed", err)));
  }

  private void pollRejectedExecutions() {
    final int rejectedExecutions = rejectedExecutionCounter.poll();
    if (rejectedExecutions > 0) {
      LOG.trace("Rejected execution count from last 5 seconds: " + rejectedExecutions);
    }
  }

  @Override
  public void stop() {
    // Stop processing new events
    eventChannels.stop();
    threadPool.shutdownNow();
    counterMaintainer.ifPresent(Cancellable::cancel);

    // Stop async actions
    asyncRunnerFactory.getAsyncRunners().forEach(AsyncRunner::shutdown);

    // Stop services. This includes closing the database.
    getServiceController()
        .stop()
        .orTimeout(30, TimeUnit.SECONDS)
        .handleException(error -> LOG.error("Failed to stop services", error))
        .thenCompose(__ -> metricsEndpoint.stop())
        .orTimeout(5, TimeUnit.SECONDS)
        .handleException(error -> LOG.debug("Failed to stop metrics", error))
        .thenRun(vertx::close)
        .join();
  }
}
