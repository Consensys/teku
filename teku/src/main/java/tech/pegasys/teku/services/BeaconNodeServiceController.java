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

import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.services.remotevalidator.RemoteValidatorService;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.validator.client.ValidatorClientService;

public class BeaconNodeServiceController extends ServiceController {

  public BeaconNodeServiceController(
      TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    // Note services will be started in the order they are added here.
    services.add(new StorageService(serviceConfig));
    services.add(new BeaconChainService(tekuConfig.beaconChain(), serviceConfig));
    if (serviceConfig.getConfig().isRemoteValidatorApiEnabled()) {
      services.add(new RemoteValidatorService(serviceConfig));
    } else {
      services.add(ValidatorClientService.create(serviceConfig));
    }
    services.add(new TimerService(serviceConfig));
    if (!serviceConfig.getConfig().isInteropEnabled()
        && serviceConfig.getConfig().isEth1Enabled()) {
      services.add(new PowchainService(serviceConfig));
    }
  }
}
