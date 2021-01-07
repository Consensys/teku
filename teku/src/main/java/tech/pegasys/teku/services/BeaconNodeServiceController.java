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
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.validator.client.ValidatorClientService;

public class BeaconNodeServiceController extends ServiceController {

  public BeaconNodeServiceController(
      TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    // Note services will be started in the order they are added here.
    services.add(new StorageService(serviceConfig, tekuConfig.storageConfiguration()));
    services.add(new BeaconChainService(serviceConfig, tekuConfig.beaconChain()));
    services.add(ValidatorClientService.create(serviceConfig, tekuConfig.validatorClient()));
    services.add(new TimerService(serviceConfig));
    powchainService(tekuConfig, serviceConfig).ifPresent(services::add);
  }

  private Optional<PowchainService> powchainService(
      TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    if (tekuConfig.beaconChain().interopConfig().isInteropEnabled()
        || !tekuConfig.powchain().isEnabled()) {
      return Optional.empty();
    }

    return Optional.of(new PowchainService(serviceConfig, tekuConfig.powchain()));
  }
}
