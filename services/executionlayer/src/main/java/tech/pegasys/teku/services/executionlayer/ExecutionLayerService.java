/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.services.executionlayer;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.config.Constants.EXECUTION_TIMEOUT;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerChannelImpl;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;

public class ExecutionLayerService extends Service implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final EventLogger eventLogger;
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider;
  private final Optional<ExecutionWeb3jClientProvider> builderWeb3jClientProvider;
  private final ExecutionLayerChannel executionLayerChannel;

  private final AtomicBoolean builderHealthCheckFailed = new AtomicBoolean(false);

  public static ExecutionLayerService create(
      final ServiceConfig serviceConfig,
      final EventLogger eventLogger,
      final ExecutionLayerConfiguration config) {
    final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
        ExecutionWeb3jClientProvider.create(
            config.getEngineEndpoint(),
            serviceConfig.getTimeProvider(),
            EXECUTION_TIMEOUT,
            config.getEngineJwtSecretFile(),
            serviceConfig.getDataDirLayout().getBeaconDataDirectory());

    final Optional<ExecutionWeb3jClientProvider> builderWeb3jClientProvider =
        config
            .getBuilderEndpoint()
            .map(
                builderEndpoint ->
                    ExecutionWeb3jClientProvider.create(
                        builderEndpoint,
                        serviceConfig.getTimeProvider(),
                        EXECUTION_TIMEOUT,
                        Optional.empty(),
                        serviceConfig.getDataDirLayout().getBeaconDataDirectory()));

    final boolean builderIsStub =
        builderWeb3jClientProvider.map(ExecutionWeb3jClientProvider::isStub).orElse(false);

    checkState(
        engineWeb3jClientProvider.isStub() == builderIsStub,
        "mixed configuration with stubbed and non-stubbed execution layer endpoints is not supported");

    final String endpoint = engineWeb3jClientProvider.getEndpoint();
    LOG.info("Using execution engine at {}", endpoint);

    final ExecutionLayerChannel executionLayerChannel;
    if (engineWeb3jClientProvider.isStub()) {
      eventLogger.executionLayerStubEnabled();
      executionLayerChannel =
          new ExecutionLayerChannelStub(config.getSpec(), serviceConfig.getTimeProvider(), true);
    } else {
      final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();
      executionLayerChannel =
          ExecutionLayerChannelImpl.create(
              engineWeb3jClientProvider.getWeb3JClient(),
              builderWeb3jClientProvider.map(ExecutionWeb3jClientProvider::getWeb3JClient),
              config.getEngineVersion(),
              config.getSpec(),
              metricsSystem);
    }

    return new ExecutionLayerService(
        serviceConfig.getEventChannels(),
        eventLogger,
        engineWeb3jClientProvider,
        builderWeb3jClientProvider,
        executionLayerChannel);
  }

  ExecutionLayerService(
      final EventChannels eventChannels,
      final EventLogger eventLogger,
      final ExecutionWeb3jClientProvider engineWeb3jClientProvider,
      final Optional<ExecutionWeb3jClientProvider> builderWeb3jClientProvider,
      final ExecutionLayerChannel executionLayerChannel) {
    this.eventChannels = eventChannels;
    this.eventLogger = eventLogger;
    this.engineWeb3jClientProvider = engineWeb3jClientProvider;
    this.builderWeb3jClientProvider = builderWeb3jClientProvider;
    this.executionLayerChannel = executionLayerChannel;
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels
        .subscribe(SlotEventsChannel.class, this)
        .subscribe(ExecutionLayerChannel.class, executionLayerChannel);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  public Optional<ExecutionWeb3jClientProvider> getEngineWeb3jClientProvider() {
    return engineWeb3jClientProvider.isStub()
        ? Optional.empty()
        : Optional.of(engineWeb3jClientProvider);
  }

  @Override
  public void onSlot(UInt64 slot) {
    performBuilderHealthCheck();
  }

  private void performBuilderHealthCheck() {
    if (builderWeb3jClientProvider.isEmpty()) {
      return;
    }
    executionLayerChannel
        .builderStatus()
        .thenAccept(
            status -> {
              if (status.hasFailed()) {
                builderHealthCheckFailed.set(true);
                eventLogger.executionBuilderIsOffline(status.getErrorMessage());
              } else {
                if (builderHealthCheckFailed.compareAndSet(true, false)) {
                  eventLogger.executionBuilderIsBackOnline();
                }
              }
            })
        .reportExceptions();
  }
}
