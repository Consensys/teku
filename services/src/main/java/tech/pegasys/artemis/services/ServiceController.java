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

import com.google.common.eventbus.EventBus;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tech.pegasys.artemis.util.cli.CommandLineArguments;

public class ServiceController {

  private ServiceInterface beaconChainService;
  private ServiceInterface powchainService;
  private ServiceInterface chainStorageService;

  private final ExecutorService beaconChainExecuterService = Executors.newSingleThreadExecutor();
  private final ExecutorService powchainExecuterService = Executors.newSingleThreadExecutor();
  private final ExecutorService chainStorageExecutorService = Executors.newSingleThreadExecutor();

  // initialize/register all services
  public <U extends ServiceInterface, V extends ServiceInterface, W extends ServiceInterface>
      void initAll(
          EventBus eventBus,
          ServiceConfig config,
          Class<U> beaconChainServiceType,
          Class<V> powchainServiceType,
          Class<W> chainStorageServiceType) {
    beaconChainService = ServiceFactory.getInstance(beaconChainServiceType).getInstance();
    powchainService = ServiceFactory.getInstance(powchainServiceType).getInstance();
    chainStorageService = ServiceFactory.getInstance(chainStorageServiceType).getInstance();

    beaconChainService.init(config);
    powchainService.init(config);
    chainStorageService.init(config);
  }

  public void startAll(CommandLineArguments cliArgs) {

    // start all services
    beaconChainExecuterService.execute(beaconChainService);
    powchainExecuterService.execute(powchainService);
    chainStorageExecutorService.execute(chainStorageService);
  }

  public void stopAll(CommandLineArguments cliArgs) {
    // stop all services
    beaconChainExecuterService.shutdown();
    if (!Objects.isNull(beaconChainService)) {
      beaconChainService.stop();
    }
    powchainExecuterService.shutdown();
    if (!Objects.isNull(powchainService)) {
      powchainService.stop();
    }
    chainStorageExecutorService.shutdown();
    if (!Objects.isNull(chainStorageService)) {
      chainStorageService.stop();
    }
  }
}
