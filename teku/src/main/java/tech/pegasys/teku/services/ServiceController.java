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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.client.ValidatorClientService;

public class ServiceController extends Service {

  private final List<Service> services = new ArrayList<>();

  public ServiceController(final ServiceConfig config) {
    // Note services will be started in the order they are added here.
    services.add(new StorageService(config));
    services.add(new BeaconChainService(config));
    services.add(ValidatorClientService.create(config));
    services.add(new TimerService(config));
    if (!config.getConfig().isInteropEnabled() && config.getConfig().isEth1Enabled()) {
      services.add(new PowchainService(config));
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    final Iterator<Service> iterator = services.iterator();
    SafeFuture<?> startupFuture = iterator.next().start();
    while (iterator.hasNext()) {
      final Service nextService = iterator.next();
      startupFuture = startupFuture.thenCompose(__ -> nextService.start());
    }
    return startupFuture;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(services.stream().map(Service::stop).toArray(SafeFuture[]::new));
  }
}
