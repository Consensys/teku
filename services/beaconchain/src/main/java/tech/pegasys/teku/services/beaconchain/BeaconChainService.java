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

package tech.pegasys.teku.services.beaconchain;

import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.util.async.SafeFuture;

public class BeaconChainService extends Service {

  private final BeaconChainController controller;

  public BeaconChainService(final ServiceConfig config) {
    this.controller =
        new BeaconChainController(
            config.getTimeProvider(),
            config.getEventBus(),
            config.getEventChannels(),
            config.getMetricsSystem(),
            config.getConfig());
  }

  @Override
  protected SafeFuture<?> doStart() {
    return this.controller.start();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return this.controller.stop();
  }
}
