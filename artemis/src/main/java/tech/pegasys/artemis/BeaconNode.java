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

import static tech.pegasys.teku.logging.StatusLogger.STDOUT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.vertx.core.Vertx;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.data.recorder.SSZTransitionRecorder;
import tech.pegasys.artemis.events.ChannelExceptionHandler;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.metrics.MetricsEndpoint;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.SystemTimeProvider;
import tech.pegasys.teku.logging.StatusLogger;
import tech.pegasys.teku.logging.StatusLogger.Color;

public class BeaconNode {

  private final Vertx vertx = Vertx.vertx();
  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("events-%d").build());

  private final ServiceController serviceController = new ServiceController();
  private final ServiceConfig serviceConfig;
  private final EventChannels eventChannels;
  private final MetricsEndpoint metricsEndpoint;
  private final EventBus eventBus;

  BeaconNode(final Optional<Level> loggingLevel, final ArtemisConfiguration config) {

    metricsEndpoint = new MetricsEndpoint(config, vertx);
    final MetricsSystem metricsSystem = metricsEndpoint.getMetricsSystem();
    final EventBusExceptionHandler subscriberExceptionHandler =
        new EventBusExceptionHandler(STDOUT);
    this.eventChannels = new EventChannels(subscriberExceptionHandler, metricsSystem);
    this.eventBus = new AsyncEventBus(threadPool, subscriberExceptionHandler);

    this.serviceConfig =
        new ServiceConfig(new SystemTimeProvider(), eventBus, eventChannels, metricsSystem, config);
    Constants.setConstants(config.getConstants());

    final String transitionRecordDir = config.getTransitionRecordDir();
    if (transitionRecordDir != null) {
      eventBus.register(new SSZTransitionRecorder(Path.of(transitionRecordDir)));
    }

    // set log level per CLI flags
    loggingLevel.ifPresent(
        level -> {
          System.out.println("Setting logging level to " + level.name());
          Configurator.setAllLevels("", level);
        });
  }

  public void start() {

    try {
      this.serviceConfig.getConfig().validateConfig();
      metricsEndpoint.start();
      // Initialize services
      serviceController.initAll(
          serviceConfig,
          BeaconChainService.class,
          PowchainService.class,
          ChainStorageService.class);

      // Start services
      serviceController.startAll();

    } catch (final CompletionException | IllegalArgumentException e) {
      STDOUT.log(Level.FATAL, "Startup failed", e);
    }
  }

  public void stop() {
    serviceController.stopAll();
    eventChannels.stop();
    metricsEndpoint.stop();
  }
}

@VisibleForTesting
final class EventBusExceptionHandler
    implements SubscriberExceptionHandler, ChannelExceptionHandler {

  private final StatusLogger logger;

  EventBusExceptionHandler(final StatusLogger logger) {
    this.logger = logger;
  }

  @Override
  public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
    handleException(
        exception,
        "event '"
            + context.getEvent().getClass().getName()
            + "'"
            + " in handler '"
            + context.getSubscriber().getClass().getName()
            + "'"
            + " (method  '"
            + context.getSubscriberMethod().getName()
            + "')");
  }

  @Override
  public void handleException(
      final Throwable error,
      final Object subscriber,
      final Method invokedMethod,
      final Object[] args) {
    handleException(
        error,
        "event '"
            + invokedMethod.getDeclaringClass()
            + "."
            + invokedMethod.getName()
            + "' in handler '"
            + subscriber.getClass().getName()
            + "'");
  }

  private void handleException(final Throwable exception, final String subscriberDescription) {
    if (isSpecFailure(exception)) {
      logger.log(Level.WARN, specFailedMessage(exception, subscriberDescription), exception);
    } else {
      logger.log(
          Level.FATAL,
          unexpectedExceptionMessage(exception, subscriberDescription),
          exception,
          Color.RED);
    }
  }

  private static boolean isSpecFailure(final Throwable exception) {
    return exception instanceof IllegalArgumentException;
  }

  private static String unexpectedExceptionMessage(
      final Throwable exception, final String subscriberDescription) {
    return "PLEASE FIX OR REPORT | Unexpected exception thrown for "
        + subscriberDescription
        + ": "
        + exception;
  }

  private static String specFailedMessage(
      final Throwable exception, final String subscriberDescription) {
    return "Spec failed for " + subscriberDescription + ": " + exception;
  }
}
