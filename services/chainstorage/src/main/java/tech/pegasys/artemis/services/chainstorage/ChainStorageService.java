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

import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.storage.ChainStorageServer;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ChainStorageService extends Service {

  private final ChainStorageServer server;

  public ChainStorageService(final ServiceConfig serviceConfig) {
    this.server = new ChainStorageServer(serviceConfig.getEventBus(), serviceConfig.getConfig());
  }

  @Override
  protected SafeFuture<?> doStart() {
    server.start();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
