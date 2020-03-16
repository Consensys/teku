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

import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.storage.DatabaseStorageException;
import tech.pegasys.artemis.util.cli.LogTypeConverter;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.teku.logging.LoggingConfigurator;

@Command(
    name = "teku",
    subcommands = {
      TransitionCommand.class,
      PeerCommand.class,
      DepositCommand.class,
      GenesisCommand.class
    },
    abbreviateSynopsis = true,
    description = "Run the Teku beacon chain client and validator",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BeaconNodeCommand implements Callable<Integer> {

  @Option(
      names = {"-l", "--logging"},
      converter = LogTypeConverter.class,
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).")
  private Level logLevel;

  @Option(
      names = {"-c", "--config"},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the config file")
  private String configFile = "./config/config.toml";

  public String getConfigFile() {
    return configFile;
  }

  @Override
  public Integer call() {
    try {

      setLogLevels();

      final BeaconNode node = new BeaconNode(ArtemisConfiguration.fromFile(getConfigFile()));
      node.start();

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("Teku is shutting down");
                    node.stop();
                  }));
      return 0;
    } catch (final DatabaseStorageException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (final Throwable t) {
      System.err.println("Teku failed to start.");
      t.printStackTrace();
      System.exit(1);
    }
    return 1;
  }

  private void setLogLevels() {
    if (logLevel != null) {
      // set log level per CLI flags
      LoggingConfigurator.setAllLevels(logLevel);
    }
  }
}
