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

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tech.pegasys.artemis.cli.CommandLineArguments;

public class ServiceController {

  private static ServiceInterface beaconChainService;
  private static ServiceInterface powchainService;
  private static ServiceInterface p2pService;
  private static ServiceInterface chainStorageService;

  private static final ExecutorService beaconChainExecuterService =
      Executors.newSingleThreadExecutor();
  private static final ExecutorService powchainExecuterService =
      Executors.newSingleThreadExecutor();
  private static final ExecutorService p2pExecuterService = Executors.newSingleThreadExecutor();
  private static final ExecutorService chainStorageExecutorService =
      Executors.newSingleThreadExecutor();

  // initialize/register all services
  public static <
          U extends ServiceInterface,
          V extends ServiceInterface,
          W extends ServiceInterface,
          X extends ServiceInterface>
      void initAll(
          CommandLineArguments cliArgs,
          Class<U> beaconChainServiceType,
          Class<V> powchainServiceType,
          Class<W> p2pServiceType,
          Class<X> chainStorageServiceType) {
    beaconChainService = ServiceFactory.getInstance(beaconChainServiceType).getInstance();
    powchainService = ServiceFactory.getInstance(powchainServiceType).getInstance();
    p2pService = ServiceFactory.getInstance(p2pServiceType).getInstance();
    chainStorageService = ServiceFactory.getInstance(chainStorageServiceType).getInstance();

    EventBus eventBus = new AsyncEventBus(Executors.newCachedThreadPool());
    beaconChainService.init(eventBus);
    if (!cliArgs.getPoWChainServiceDisabled()) {
      powchainService.init(eventBus);
    }
    p2pService.init(eventBus);
    chainStorageService.init(eventBus);

    // Validator Service

    // RPC Service
  }

  public static void startAll(CommandLineArguments cliArgs) {

    // start all services
    beaconChainExecuterService.execute(beaconChainService);
    if (!cliArgs.getPoWChainServiceDisabled()) {
      powchainExecuterService.execute(powchainService);
    }
    p2pExecuterService.execute(p2pService);
    chainStorageExecutorService.execute(chainStorageService);
  }

  public static void stopAll(CommandLineArguments cliArgs) {
    // stop all services
    beaconChainExecuterService.shutdown();
    beaconChainService.stop();
    powchainExecuterService.shutdown();
    powchainService.stop();
    p2pExecuterService.shutdown();
    p2pService.stop();
    chainStorageExecutorService.shutdown();
    chainStorageService.stop();
  }
}
