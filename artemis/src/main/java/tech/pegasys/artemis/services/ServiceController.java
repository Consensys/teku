/*
 * Copyright 2018 ConsenSys AG.
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tech.pegasys.artemis.cli.CommandLineArguments;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.powchain.PowchainService;

public class ServiceController {

  private static final BeaconChainService beaconChainService =
      ServiceFactory.getInstance(BeaconChainService.class).getInstance();;
  private static final PowchainService powchainService =
      ServiceFactory.getInstance(PowchainService.class).getInstance();
  private static final ExecutorService beaconChainExecuterService =
      Executors.newSingleThreadExecutor();
  private static final ExecutorService powchainExecuterService =
      Executors.newSingleThreadExecutor();
  // initialize/register all services
  public static void initAll(CommandLineArguments cliArgs) {

    beaconChainService.init();
    if (!cliArgs.getPoWChainServiceDisabled()) {
      powchainService.init();
    }

    // Validator Service

    // P2P Service

    // RPC Service
  }

  public static void startAll(CommandLineArguments cliArgs) {

    // start all services
    beaconChainExecuterService.execute(beaconChainService);
    if (!cliArgs.getPoWChainServiceDisabled()) {
      powchainExecuterService.execute(powchainService);
    }
  }

  public static void stopAll(CommandLineArguments cliArgs) {
    // stop all services
    beaconChainExecuterService.shutdown();
    beaconChainService.stop();
    powchainExecuterService.shutdown();
    powchainService.stop();
  }
}
