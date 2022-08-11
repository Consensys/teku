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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.BuilderBidValidatorImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider;
  private final ExecutionLayerManager executionLayerManager;

  public static ExecutionLayerService create(
      final ServiceConfig serviceConfig, final ExecutionLayerConfiguration config) {

    final Path beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
        ExecutionWeb3jClientProvider.create(
            config.getEngineEndpoint(),
            EL_ENGINE_BLOCK_EXECUTION_TIMEOUT,
            true,
            config.getEngineJwtSecretFile(),
            beaconDataDirectory,
            timeProvider);

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

    final ExecutionLayerManager executionLayerManager;
    if (engineWeb3jClientProvider.isStub()) {
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
      executionLayerManager =
          new ExecutionLayerManagerStub(
              config.getSpec(), timeProvider, true, terminalBlockHashInTTDMode);
    } else {
      final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();

      final ExecutionEngineClient executionEngineClient =
          ExecutionLayerManagerImpl.createEngineClient(
              config.getEngineVersion(),
              engineWeb3jClientProvider.getWeb3JClient(),
              timeProvider,
              metricsSystem);
      final Optional<ExecutionBuilderClient> executionBuilderClient =
          builderRestClientProvider.map(
              restClientProvider ->
                  ExecutionLayerManagerImpl.createBuilderClient(
                      restClientProvider.getRestClient(),
                      config.getSpec(),
                      timeProvider,
                      metricsSystem));

      executionLayerManager =
          ExecutionLayerManagerImpl.create(
              EVENT_LOG,
              executionEngineClient,
              executionBuilderClient,
              config.getSpec(),
              metricsSystem,
              new BuilderBidValidatorImpl(EVENT_LOG));
    }

    return new ExecutionLayerService(
        serviceConfig.getEventChannels(), engineWeb3jClientProvider, executionLayerManager);
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
