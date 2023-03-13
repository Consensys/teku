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

package tech.pegasys.teku.services.executionlayer;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_CALL_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_BLOCK_EXECUTION_TIMEOUT;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.BlobsBundleValidator;
import tech.pegasys.teku.ethereum.executionlayer.BlobsBundleValidatorImpl;
import tech.pegasys.teku.ethereum.executionlayer.BuilderBidValidatorImpl;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreakerImpl;
import tech.pegasys.teku.ethereum.executionlayer.EngineApiCapabilitiesProvider;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientEngineApiCapabilitiesProvider;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandler;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandlerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub;
import tech.pegasys.teku.ethereum.executionlayer.LocallySupportedEngineApiCapabilitiesProvider;
import tech.pegasys.teku.ethereum.executionlayer.MilestoneBasedExecutionJsonRpcMethodsResolver;
import tech.pegasys.teku.ethereum.executionlayer.NegotiatedExecutionJsonRpcMethodsResolver;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class ExecutionLayerService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider;
  private final ExecutionLayerManager executionLayerManager;

  public static ExecutionLayerService create(
      final ServiceConfig serviceConfig, final ExecutionLayerConfiguration config) {

    final Path beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();
    final Supplier<AsyncRunner> asyncRunnerSupplier =
        () -> serviceConfig.createAsyncRunner("execution-layer", 1);

    final ExecutionClientEventsChannel executionClientEventsPublisher =
        serviceConfig.getEventChannels().getPublisher(ExecutionClientEventsChannel.class);

    final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
        ExecutionWeb3jClientProvider.create(
            config.getEngineEndpoint(),
            EL_ENGINE_BLOCK_EXECUTION_TIMEOUT,
            true,
            config.getEngineJwtSecretFile(),
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
      executionLayerManager =
          createRealExecutionLayerManager(
              serviceConfig,
              config,
              asyncRunnerSupplier,
              engineWeb3jClientProvider,
              builderRestClientProvider,
              builderCircuitBreaker);
    }

    return new ExecutionLayerService(
        serviceConfig.getEventChannels(), engineWeb3jClientProvider, executionLayerManager);
  }

  private static ExecutionLayerManager createStubExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    EVENT_LOG.executionLayerStubEnabled();
    Optional<Bytes32> terminalBlockHashInTTDMode = Optional.empty();
    if (config.getEngineEndpoint().startsWith(STUB_ENDPOINT_PREFIX + ":0x")) {
      try {
        terminalBlockHashInTTDMode =
            Optional.of(
                Bytes32.fromHexStringStrict(
                    config
                        .getEngineEndpoint()
                        .substring(STUB_ENDPOINT_PREFIX.length() + ":0x".length())));
      } catch (Exception ex) {
        LOG.warn("Unable to parse terminal block hash from stub endpoint.", ex);
      }
    }

    return new ExecutionLayerManagerStub(
        config.getSpec(),
        serviceConfig.getTimeProvider(),
        true,
        terminalBlockHashInTTDMode,
        builderCircuitBreaker);
  }

  private static ExecutionLayerManager createRealExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final Supplier<AsyncRunner> asyncRunnerSupplier,
      final ExecutionWeb3jClientProvider engineWeb3jClientProvider,
      final Optional<RestClientProvider> builderRestClientProvider,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionEngineClient executionEngineClient =
        ExecutionLayerManagerImpl.createEngineClient(
            config.getEngineVersion(),
            engineWeb3jClientProvider.getWeb3JClient(),
            timeProvider,
            metricsSystem);

    final EngineApiCapabilitiesProvider localEngineApiCapabilitiesProvider =
        new LocallySupportedEngineApiCapabilitiesProvider(config.getSpec(), executionEngineClient);
    final MilestoneBasedExecutionJsonRpcMethodsResolver milestoneBasedMethodResolver =
        new MilestoneBasedExecutionJsonRpcMethodsResolver(
            config.getSpec(), localEngineApiCapabilitiesProvider);

    final ExecutionClientHandler executionClientHandler;
    if (config.isExchangeCapabilitiesEnabled()) {
      final ExecutionClientEngineApiCapabilitiesProvider remoteEngineApiCapabilitiesProvider =
          new ExecutionClientEngineApiCapabilitiesProvider(
              asyncRunnerSupplier.get(), executionEngineClient, localEngineApiCapabilitiesProvider);

      final NegotiatedExecutionJsonRpcMethodsResolver negotiatedMethodsResolver =
          new NegotiatedExecutionJsonRpcMethodsResolver(
              localEngineApiCapabilitiesProvider,
              remoteEngineApiCapabilitiesProvider,
              milestoneBasedMethodResolver);

      executionClientHandler = new ExecutionClientHandlerImpl(negotiatedMethodsResolver);
    } else {
      executionClientHandler = new ExecutionClientHandlerImpl(milestoneBasedMethodResolver);
    }

    final Optional<BuilderClient> builderClient =
        builderRestClientProvider.map(
            restClientProvider ->
                ExecutionLayerManagerImpl.createBuilderClient(
                    restClientProvider.getRestClient(),
                    config.getSpec(),
                    timeProvider,
                    metricsSystem));

    final BlobsBundleValidator blobsBundleValidator =
        config.getSpec().isMilestoneSupported(SpecMilestone.DENEB)
            ? new BlobsBundleValidatorImpl(
                (MiscHelpersDeneb) config.getSpec().forMilestone(SpecMilestone.DENEB).miscHelpers())
            : BlobsBundleValidator.NOOP;

    return ExecutionLayerManagerImpl.create(
        EVENT_LOG,
        executionClientHandler,
        builderClient,
        config.getSpec(),
        metricsSystem,
        new BuilderBidValidatorImpl(EVENT_LOG),
        builderCircuitBreaker,
        blobsBundleValidator,
        config.getBuilderBidChallengePercentage());
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
