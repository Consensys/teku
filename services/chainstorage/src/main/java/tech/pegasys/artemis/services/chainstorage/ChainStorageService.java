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

package tech.pegasys.artemis.services.chainstorage;

import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.storage.ChainStorageServer;

public class ChainStorageService implements ServiceInterface {

  @Override
  public void init(ServiceConfig config) {
    ChainStorageServer server = new ChainStorageServer(config.getEventBus(), config.getConfig());
    server.start();
  }

  @Override
  public void run() {}

  @Override
  public void stop() {
    STATUS_LOG.log(Level.DEBUG, "ChainStorageService.stop()");
  }
}
