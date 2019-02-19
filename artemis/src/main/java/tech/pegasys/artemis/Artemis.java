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

package tech.pegasys.artemis;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import picocli.CommandLine;
import tech.pegasys.artemis.services.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.beaconnode.BeaconNodeService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.cli.CommandLineArguments;

public final class Artemis {

  public static void main(final String... args) {
    try {
      Security.addProvider(new BouncyCastleProvider());
      // Process Command Line Args
      CommandLineArguments cliArgs = new CommandLineArguments();
      CommandLine commandLine = new CommandLine(cliArgs);
      commandLine.parse(args);
      if (commandLine.isUsageHelpRequested()) {
        commandLine.usage(System.out);
        return;
      }
      // Detect SIGTERM
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                @Override
                public void run() {
                  System.out.println("Artemis is shutting down");
                  ServiceController.stopAll(cliArgs);
                }
              });
      // Initialize services
      ServiceController.initAll(
          cliArgs,
          BeaconChainService.class,
          PowchainService.class,
          BeaconNodeService.class,
          ChainStorageService.class);
      // Start services
      ServiceController.startAll(cliArgs);
    } catch (Exception e) {
      System.out.println(e.toString());
    }
  }
}
