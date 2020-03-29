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

package tech.pegasys.artemis.services;

import java.util.Optional;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.services.timer.TimerService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.client.ValidatorClientService;

public class ServiceController extends Service {
  private final BeaconChainService beaconChainService;
  private final ChainStorageService chainStorageService;
  private final TimerService timerService;
  private final Optional<PowchainService> powchainService;
  private final ValidatorClientService validatorClientService;

  public ServiceController(final ServiceConfig config) {
    timerService = new TimerService(config);
    beaconChainService = new BeaconChainService(config);
    chainStorageService = new ChainStorageService(config);
    powchainService =
        config.getConfig().isInteropEnabled()
            ? Optional.empty()
            : Optional.of(new PowchainService(config));
    validatorClientService = ValidatorClientService.create(config);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        timerService.start(),
        chainStorageService.start(),
        beaconChainService.start(),
        validatorClientService.start(),
        powchainService.map(PowchainService::start).orElse(SafeFuture.completedFuture(null)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        timerService.stop(),
        chainStorageService.stop(),
        beaconChainService.stop(),
        validatorClientService.stop(),
        powchainService.map(PowchainService::stop).orElse(SafeFuture.completedFuture(null)));
  }
}
