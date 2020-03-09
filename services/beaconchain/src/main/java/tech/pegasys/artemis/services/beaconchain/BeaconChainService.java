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

package tech.pegasys.artemis.services.beaconchain;

import static tech.pegasys.teku.logging.ALogger.STDOUT;

import java.util.Objects;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;

public class BeaconChainService implements ServiceInterface {
  private BeaconChainController controller;

  public BeaconChainService() {}

  @Override
  public void init(ServiceConfig config) {
    this.controller =
        new BeaconChainController(
            config.getTimeProvider(),
            config.getEventBus(),
            config.getEventChannels(),
            config.getMetricsSystem(),
            config.getConfig());
    this.controller.initAll();
  }

  @Override
  public void run() {
    this.controller.start();
  }

  @Override
  public void stop() {
    STDOUT.log(Level.DEBUG, "BeaconChainService.stop()");
    if (!Objects.isNull(controller)) {
      this.controller.stop();
    }
  }
}
