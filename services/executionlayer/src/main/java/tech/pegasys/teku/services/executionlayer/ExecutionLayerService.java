/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_CALL_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_BLOCK_EXECUTION_TIMEOUT;

import com.google.common.base.Splitter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.OkHttpClientCreator;
import tech.pegasys.teku.ethereum.executionclient.OkHttpExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.OkHttpExecutionEngineClientFactory;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.BuilderBidValidatorImpl;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreakerImpl;
import tech.pegasys.teku.ethereum.executionlayer.EngineCapabilitiesMonitor;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandler;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandlerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub;
import tech.pegasys.teku.ethereum.executionlayer.MilestoneBasedEngineJsonRpcMethodsResolver;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider;
  private final ExecutionLayerManager executionLayerManager;

  public static ExecutionLayerService create(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final Spec spec) {

    final Path beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionClientEventsChannel executionClientEventsPublisher =
        serviceConfig.getEventChannels().getPublisher(ExecutionClientEventsChannel.class);

    // TODO-lucas This is eventually used on PowchainService. However this is likely legacy and can
    // be removed as we
    // don't care about fetching deposits from chain anymore.
    final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
        ExecutionWeb3jClientProvider.create(
            config.getEngineEndpoint(),
            EL_ENGINE_BLOCK_EXECUTION_TIMEOUT,
            true,
            config.getEngineJwtSecretFile(),
            config.getEngineJwtClaimId(),
            beaconDataDirectory,
            timeProvider,
            executionClientEventsPublisher);

    final Optional<RestClientProvider> builderRestClientProvider =
        config
            .getBuilderEndpoint()
            .map(
                builderEndpoint ->
                    RestClientProvider.create(
                        builderEndpoint,
                        BUILDER_CALL_TIMEOUT,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        beaconDataDirectory,
                        timeProvider));

    final boolean builderIsStub =
        builderRestClientProvider.map(RestClientProvider::isStub).orElse(false);

    checkState(
        engineWeb3jClientProvider.isStub() == builderIsStub || builderRestClientProvider.isEmpty(),
        "mixed configuration with stubbed and non-stubbed execution layer endpoints is not supported");

    final String endpoint = engineWeb3jClientProvider.getEndpoint();
    LOG.info("Using execution engine at {}", endpoint);

    final BuilderCircuitBreaker builderCircuitBreaker;
    if (config.isBuilderCircuitBreakerEnabled()) {
      LOG.info("Enabling Builder Circuit Breaker");
      builderCircuitBreaker =
          new BuilderCircuitBreakerImpl(
              config.getSpec(),
              config.getBuilderCircuitBreakerWindow(),
              config.getBuilderCircuitBreakerAllowedFaults(),
              config.getBuilderCircuitBreakerAllowedConsecutiveFaults());
    } else {
      builderCircuitBreaker = BuilderCircuitBreaker.NOOP;
    }

    final ExecutionLayerManager executionLayerManager;
    if (engineWeb3jClientProvider.isStub()) {
      executionLayerManager =
          createStubExecutionLayerManager(serviceConfig, config, builderCircuitBreaker);
    } else {
      if (!config.isUseNewEngineApiClient()) {
        executionLayerManager =
            createRealExecutionLayerManager(
                serviceConfig,
                config,
                spec,
                new Web3JExecutionEngineClient(engineWeb3jClientProvider.getWeb3JClient()),
                builderRestClientProvider,
                builderCircuitBreaker);
      } else {
        LOG.info("Using new experimental Engine API client");
        final Optional<JwtConfig> jwtConfig =
            JwtConfig.createIfNeeded(
                true,
                config.getEngineJwtSecretFile(),
                config.getEngineJwtClaimId(),
                beaconDataDirectory);
        final OkHttpClient okHttpClient = OkHttpClientCreator.create(LOG, jwtConfig, timeProvider);
        final ExecutionEngineClient newEngineApiClient =
            OkHttpExecutionEngineClientFactory.create(
                okHttpClient,
                () -> serviceConfig.createAsyncRunner("ipc-reader", 1),
                config.getEngineEndpoint(),
                EVENT_LOG,
                timeProvider,
                executionClientEventsPublisher,
                OkHttpExecutionEngineClient.NON_CRITICAL_METHODS);
        executionLayerManager =
            createRealExecutionLayerManager(
                serviceConfig,
                config,
                spec,
                newEngineApiClient,
                builderRestClientProvider,
                builderCircuitBreaker);
      }
    }

    return new ExecutionLayerService(
        serviceConfig.getEventChannels(), engineWeb3jClientProvider, executionLayerManager);
  }

  private static ExecutionLayerManager createStubExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    EVENT_LOG.executionLayerStubEnabled();

    final List<String> endpointWithAdditionalConfigs =
        Splitter.on(':').splitToList(config.getEngineEndpoint());
    final List<String> additionalConfigs =
        endpointWithAdditionalConfigs.subList(1, endpointWithAdditionalConfigs.size());

    return new ExecutionLayerManagerStub(
        config.getSpec(),
        serviceConfig.getTimeProvider(),
        true,
        additionalConfigs,
        builderCircuitBreaker);
  }

  private static ExecutionLayerManager createRealExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final Spec spec,
      final ExecutionEngineClient rawEngineClient,
      final Optional<RestClientProvider> builderRestClientProvider,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionEngineClient executionEngineClient =
        ExecutionLayerManagerImpl.createEngineClient(rawEngineClient, timeProvider, metricsSystem);

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(config.getSpec(), executionEngineClient);

    final ExecutionClientHandler executionClientHandler =
        new ExecutionClientHandlerImpl(
            config.getSpec(), executionEngineClient, engineMethodsResolver);

    if (config.isExchangeCapabilitiesMonitoringEnabled()) {
      final EngineCapabilitiesMonitor engineCapabilitiesMonitor =
          new EngineCapabilitiesMonitor(
              config.getSpec(), EVENT_LOG, engineMethodsResolver, executionEngineClient);
      serviceConfig
          .getEventChannels()
          .subscribe(SlotEventsChannel.class, engineCapabilitiesMonitor);
    }

    final Optional<BuilderClient> builderClient =
        builderRestClientProvider.map(
            restClientProvider ->
                ExecutionLayerManagerImpl.createBuilderClient(
                    restClientProvider.getRestClient(),
                    config.getSpec(),
                    timeProvider,
                    metricsSystem,
                    config.getBuilderSetUserAgentHeader()));

    return ExecutionLayerManagerImpl.create(
        EVENT_LOG,
        executionClientHandler,
        spec,
        builderClient,
        metricsSystem,
        new BuilderBidValidatorImpl(config.getSpec(), EVENT_LOG),
        builderCircuitBreaker,
        config.getBuilderBidCompareFactor(),
        config.getUseShouldOverrideBuilderFlag());
  }

  ExecutionLayerService(
      final EventChannels eventChannels,
      final ExecutionWeb3jClientProvider engineWeb3jClientProvider,
      final ExecutionLayerManager executionLayerManager) {
    this.eventChannels = eventChannels;
    this.engineWeb3jClientProvider = engineWeb3jClientProvider;
    this.executionLayerManager = executionLayerManager;
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels
        .subscribe(SlotEventsChannel.class, executionLayerManager)
        .subscribe(ExecutionLayerChannel.class, executionLayerManager);
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
}
