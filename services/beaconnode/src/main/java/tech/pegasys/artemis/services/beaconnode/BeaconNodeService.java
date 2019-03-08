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

package tech.pegasys.artemis.services.beaconnode;

import com.google.common.eventbus.EventBus;
import java.io.IOException;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.networking.p2p.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.util.alogger.ALogger;

public class BeaconNodeService implements ServiceInterface {

  private EventBus eventBus;
  private static final ALogger LOG = new ALogger(BeaconNodeService.class.getName());
  private P2PNetwork p2pNetwork;

  public BeaconNodeService() {}

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    this.p2pNetwork = new MockP2PNetwork(eventBus);
    this.eventBus.register(this);
  }

  @Override
  public void run() {
    this.p2pNetwork.run();
  }

  @Override
  public void stop() {
    try {
      this.p2pNetwork.close();
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }
}
