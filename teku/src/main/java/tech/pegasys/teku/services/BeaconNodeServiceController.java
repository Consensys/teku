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

package tech.pegasys.teku.services;

import java.util.Optional;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.executionengine.ExecutionClientProvider;
import tech.pegasys.teku.networking.nat.NatService;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.executionengine.ExecutionEngineService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.validator.client.ValidatorClientService;

public class BeaconNodeServiceController extends ServiceController {

  public BeaconNodeServiceController(
      TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    // Note services will be started in the order they are added here.
    services.add(new StorageService(serviceConfig, tekuConfig.storageConfiguration()));
    Optional<ExecutionClientProvider> maybeExecutionClientProvider = Optional.empty();
    if (tekuConfig.executionEngine().isEnabled()) {
      // Need to make sure the execution engine is listening before starting the beacon chain
      ExecutionEngineService executionEngineService =
          new ExecutionEngineService(serviceConfig, tekuConfig.executionEngine());
      services.add(executionEngineService);
      maybeExecutionClientProvider = executionEngineService.getWeb3jClientProvider();
    }
    services.add(new BeaconChainService(serviceConfig, tekuConfig.beaconChain()));
    services.add(
        new NatService(
            tekuConfig.natConfiguration(),
            tekuConfig.network().getListenPort(),
            tekuConfig.discovery().isDiscoveryEnabled()));
    powchainService(tekuConfig, serviceConfig, maybeExecutionClientProvider)
        .ifPresent(services::add);
    services.add(ValidatorClientService.create(serviceConfig, tekuConfig.validatorClient()));
  }

  private Optional<PowchainService> powchainService(
      final TekuConfiguration tekuConfig,
      final ServiceConfig serviceConfig,
      final Optional<ExecutionClientProvider> maybeExecutionClientProvider) {
    if (tekuConfig.beaconChain().interopConfig().isInteropEnabled()
        || (!tekuConfig.powchain().isEnabled() && maybeExecutionClientProvider.isEmpty())) {
      return Optional.empty();
    }

    return Optional.of(
        new PowchainService(serviceConfig, tekuConfig.powchain(), maybeExecutionClientProvider));
  }
}
