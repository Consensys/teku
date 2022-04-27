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

package tech.pegasys.teku.services.executionengine;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.spec.config.Constants.EXECUTION_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EE_REQUESTS;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionengine.ExecutionClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionEngineChannelImpl;
import tech.pegasys.teku.ethereum.executionlayer.ThrottlingExecutionEngineChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannelStub;

public class ExecutionEngineService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final ExecutionEngineConfiguration config;
  private final MetricsSystem metricsSystem;
  private final ExecutionClientProvider web3jClientProvider;
  private final TimeProvider timeProvider;

  public ExecutionEngineService(
      final ServiceConfig serviceConfig, final ExecutionEngineConfiguration config) {
    this.eventChannels = serviceConfig.getEventChannels();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.config = config;
    this.web3jClientProvider =
        ExecutionClientProvider.create(
            config.getEndpoint(),
            serviceConfig.getTimeProvider(),
            EXECUTION_TIMEOUT,
            config.getJwtSecretFile(),
            serviceConfig.getDataDirLayout().getBeaconDataDirectory());
    this.timeProvider = serviceConfig.getTimeProvider();
  }

  @Override
  protected SafeFuture<?> doStart() {
    final String endpoint = web3jClientProvider.getEndpoint();
    LOG.info("Using execution engine at {}", endpoint);
    final ExecutionEngineChannel executionEngineChannel;
    if (web3jClientProvider.isStub()) {
      EVENT_LOG.executionEngineStubEnabled();
      executionEngineChannel = new ExecutionEngineChannelStub(config.getSpec(), timeProvider, true);
    } else {
      executionEngineChannel =
          ExecutionEngineChannelImpl.create(
              web3jClientProvider.getWeb3JClient(), config.getVersion(), config.getSpec());
    }
    final ExecutionEngineChannel executionEngine =
        new ThrottlingExecutionEngineChannel(
            executionEngineChannel, MAXIMUM_CONCURRENT_EE_REQUESTS, metricsSystem);
    eventChannels.subscribe(ExecutionEngineChannel.class, executionEngine);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  public Optional<ExecutionClientProvider> getWeb3jClientProvider() {
    return web3jClientProvider.isStub() ? Optional.empty() : Optional.of(web3jClientProvider);
  }
}
