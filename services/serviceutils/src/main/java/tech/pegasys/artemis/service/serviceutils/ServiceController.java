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

package tech.pegasys.artemis.service.serviceutils;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServiceController {

  private static final Logger LOG = LogManager.getLogger();

  private ServiceInterface beaconChainService;
  private ServiceInterface powchainService;
  private ServiceInterface chainStorageService;

  private final ExecutorService beaconChainExecuterService = Executors.newSingleThreadExecutor();
  private final ExecutorService powchainExecuterService = Executors.newSingleThreadExecutor();
  private final ExecutorService chainStorageExecutorService = Executors.newSingleThreadExecutor();

  private boolean powChainServiceActive;

  // initialize/register all services
  public <U extends ServiceInterface, V extends ServiceInterface, W extends ServiceInterface>
      void initAll(
          ServiceConfig config,
          Class<U> beaconChainServiceType,
          Class<V> powchainServiceType,
          Class<W> chainStorageServiceType) {
    powChainServiceActive = !config.getConfig().getDepositMode().equals("test");
    chainStorageService = ServiceFactory.getInstance(chainStorageServiceType).getInstance();
    beaconChainService = ServiceFactory.getInstance(beaconChainServiceType).getInstance();

    // Chain storage has to be initialized first due to node start event requiring storage service
    // to be online
    beaconChainService.init(config);
    chainStorageService.init(config);

    if (powChainServiceActive) {
      powchainService = ServiceFactory.getInstance(powchainServiceType).getInstance();
      powchainService.init(config);
    }
  }

  public void startAll() {
    // start all services
    chainStorageExecutorService.execute(chainStorageService);
    beaconChainExecuterService.execute(beaconChainService);
    if (powChainServiceActive) {
      powchainExecuterService.execute(powchainService);
    }
  }

  public void stopAll() {
    // stop all services
    LOG.debug("ServiceController.stopAll()");
    if (!Objects.isNull(beaconChainService)) {
      beaconChainService.stop();
    }
    if (!Objects.isNull(chainStorageService)) {
      chainStorageService.stop();
    }
    if (powChainServiceActive) {
      if (!Objects.isNull(powchainService)) {
        powchainService.stop();
      }
    }

    beaconChainExecuterService.shutdown();
    chainStorageExecutorService.shutdown();
    powchainExecuterService.shutdown();

    try {
      if (!beaconChainExecuterService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        beaconChainExecuterService.shutdownNow();
      }
      if (!chainStorageExecutorService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        chainStorageExecutorService.shutdownNow();
      }
      if (powChainServiceActive
          && !powchainExecuterService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        powchainExecuterService.shutdownNow();
      }
    } catch (InterruptedException e) {
      beaconChainExecuterService.shutdownNow();
      chainStorageExecutorService.shutdownNow();
      powchainExecuterService.shutdownNow();
    }
  }
}
