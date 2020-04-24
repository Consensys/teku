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

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.services.timer.TimerService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.client.ValidatorClientService;

public class ServiceController extends Service {

  private final List<Service> services = new ArrayList<>();

  public ServiceController(final ServiceConfig config) {
    services.add(new TimerService(config));
    services.add(new BeaconChainService(config));
    services.add(new ChainStorageService(config));
    services.add(ValidatorClientService.create(config));
    if (!config.getConfig().isInteropEnabled()) {
      services.add(new PowchainService(config));
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        services.stream().map(Service::start).toArray(SafeFuture[]::new));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(services.stream().map(Service::stop).toArray(SafeFuture[]::new));
  }
}
