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

package tech.pegasys.artemis.cli;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.BeaconNode;
import tech.pegasys.artemis.cli.subcommand.DepositCommand;
import tech.pegasys.artemis.cli.subcommand.GenesisCommand;
import tech.pegasys.artemis.cli.subcommand.PeerCommand;
import tech.pegasys.artemis.cli.subcommand.TransitionCommand;
import tech.pegasys.artemis.cli.util.CascadingDefaultProvider;
import tech.pegasys.artemis.cli.util.TomlConfigFileDefaultProvider;
import tech.pegasys.artemis.storage.DatabaseStorageException;
import tech.pegasys.artemis.util.cli.LogTypeConverter;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.teku.logging.LoggingConfigurator;

@SuppressWarnings("unused")
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
public class BeaconNodeCommand implements Callable<Integer>, OptionNames, DefaultOptionValues {

  @Option(
      names = {"-l", "--logging"},
      converter = LogTypeConverter.class,
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).",
      arity = "1")
  private Level logLevel;

  @Option(
      names = {"-c", CONFIG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the config file",
      arity = "1")
  private String configFile = DEFAULT_CONFIG_FILE;

  // Network
  @Option(
      names = {"-n", NETWORK_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use",
      arity = "1")
  private String network = DEFAULT_NETWORK;

  // P2P
  @Option(
      names = {P2P_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables peer to peer",
      arity = "1")
  private boolean p2pEnabled = DEFAULT_P2P_ENABLED;

  @Option(
      names = {P2P_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Peer to peer network interface",
      arity = "1")
  private String p2pInterface = DEFAULT_P2P_INTERFACE;

  @Option(
      names = {P2P_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Peer to peer port",
      arity = "1")
  private int p2pPort = DEFAULT_P2P_PORT;

  @Option(
      names = {P2P_DISCOVERY_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables discv5 discovery",
      arity = "1")
  private boolean p2pDiscoveryEnabled = DEFAULT_P2P_DISCOVERY_ENABLED;

  @Option(
      names = {P2P_DISCOVERY_BOOTNODES_OPTION_NAME},
      paramLabel = "<enode://id@host:port>",
      description = "ENR of the bootnode",
      split = ",",
      arity = "0..*")
  private ArrayList<String> p2pDiscoveryBootnodes = DEFAULT_P2P_DISCOVERY_BOOTNODES;

  @Option(
      names = {P2P_ADVERTISED_IP_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Peer to peer advertised ip",
      arity = "1")
  private String p2pAdvertisedIp = DEFAULT_P2P_ADVERTISED_IP;

  @Option(
      names = {P2P_ADVERTISED_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Peer to peer advertised port",
      arity = "1")
  private int p2pAdvertisedPort = DEFAULT_P2P_ADVERTISED_PORT;

  @Option(
      names = {P2P_PRIVATE_KEY_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "This node's private key file",
      arity = "1")
  private String p2pPrivateKeyFile = DEFAULT_P2P_PRIVATE_KEY_FILE;

  @Option(
      names = {P2P_PEER_LOWER_BOUND_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      arity = "1")
  private int p2pLowerBound = DEFAULT_P2P_PEER_LOWER_BOUND;

  @Option(
      names = {P2P_PEER_UPPER_BOUND_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      arity = "1")
  private int p2pUpperBound = DEFAULT_P2P_PEER_UPPER_BOUND;

  @Option(
      names = {P2P_STATIC_PEERS_OPTION_NAME},
      paramLabel = "<PEER_ADDRESSES>",
      description = "Static peers",
      arity = "1")
  private ArrayList<String> p2pStaticPeers = DEFAULT_P2P_STATIC_PEERS;

  // Interop

  @Option(
      hidden = true,
      names = {X_INTEROP_GENESIS_TIME_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Time of mocked genesis",
      arity = "1")
  private Integer xInteropGenesisTime = DEFAULT_X_INTEROP_GENESIS_TIME;

  @Option(
      hidden = true,
      names = {X_INTEROP_OWNED_VALIDATOR_START_INDEX_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Index of first validator owned by this node",
      arity = "1")
  private int xInteropOwnerValidatorStartIndex = DEFAULT_X_INTEROP_OWNED_VALIDATOR_START_INDEX;

  @Option(
      hidden = true,
      names = {X_INTEROP_OWNED_VALIDATOR_COUNT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Number of validators owned by this node",
      arity = "1")
  private int xInteropOwnerValidatorCount = DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT;

  @Option(
      hidden = true,
      names = {X_INTEROP_START_STATE_OPTION_NAME},
      paramLabel = "<STRING>",
      description = "Initial BeaconState to load",
      arity = "1")
  private String xInteropStartState = DEFAULT_X_INTEROP_START_STATE;

  @Option(
      hidden = true,
      names = {X_INTEROP_NUMBER_OF_VALIDATORS_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Represents the total number of validators in the network")
  private int xInteropNumberOfValidators = DEFAULT_X_INTEROP_NUMBER_OF_VALIDATORS;

  @Option(
      hidden = true,
      names = {X_INTEROP_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables developer options for testing",
      arity = "1")
  private boolean xInteropEnabled = DEFAULT_X_INTEROP_ENABLED;

  // Validator

  @Option(
      names = {VALIDATORS_KEY_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "The file to load validator keys from",
      arity = "1")
  private String validatorsKeyFile = DEFAULT_VALIDATORS_KEY_FILE;

  @Option(
      names = {VALIDATORS_KEYSTORE_FILES_OPTION_NAME},
      paramLabel = "<FILENAMES>",
      description = "The list of encrypted keystore files to load the validator keys from",
      split = ",",
      arity = "0..*")
  private ArrayList<String> validatorsKeystoreFiles = DEFAULT_VALIDATORS_KEYSTORE_FILES;

  @Option(
      names = {VALIDATORS_KEYSTORE_PASSWORD_FILES_OPTION_NAME},
      paramLabel = "<FILENAMES>",
      description = "The list of password files to decrypt the validator keystore files",
      split = ",",
      arity = "0..*")
  private ArrayList<String> validatorsKeystorePasswordFiles =
      DEFAULT_VALIDATORS_KEYSTORE_PASSWORD_FILES;

  // Deposit

  @Option(
      names = {ETH1_DEPOSIT_CONTRACT_ADDRESS_OPTION_NAME},
      paramLabel = "<ADDRESS>",
      description = "Contract address for the deposit contract",
      arity = "1")
  private String eth1DepositContractAddress = DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS;

  @Option(
      names = {ETH1_ENDPOINT_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "URL for Eth 1.0 node",
      arity = "1")
  private String eth1Endpoint = DEFAULT_ETH1_ENDPOINT;

  // Logging

  @Option(
      names = {LOG_COLOUR_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Whether Status and Event log messages include a console color display code",
      arity = "1")
  private boolean logColourEnabled = DEFAULT_LOG_COLOUR_ENABLED;

  @Option(
      names = {LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description =
          "Whether the frequent update events are logged (e.g. every slot event, with validators and attestations))",
      arity = "1")
  private boolean logIncludeEventsEnabled = DEFAULT_LOG_INCLUDE_EVENTS_ENABLED;

  @Option(
      names = {LOG_DESTINATION_OPTION_NAME},
      paramLabel = "<LOG_DESTINATION>",
      description = "Whether all logs go only to the console, only to the log file, or both",
      arity = "1")
  private String logDestination = DEFAULT_LOG_DESTINATION;

  @Option(
      names = {LOG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path containing the location (relative or absolute) and the log filename.",
      arity = "1")
  private String logFile = DEFAULT_LOG_FILE;

  @Option(
      names = {LOG_FILE_NAME_PATTERN_OPTION_NAME},
      paramLabel = "<REGEX>",
      description = "Pattern for the filename to apply to rolled over logs files.",
      arity = "1")
  private String logFileNamePattern = DEFAULT_LOG_FILE_NAME_PATTERN;

  // Output

  @Option(
      hidden = true,
      names = {X_TRANSITION_RECORD_DIRECTORY_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Directory to record transition pre and post states",
      arity = "1")
  private String xTransitionRecordDirectory = DEFAULT_X_TRANSITION_RECORD_DIRECTORY;

  // Metrics

  @Option(
      names = {METRICS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables metrics collection via Prometheus",
      arity = "1")
  private boolean metricsEnabled = DEFAULT_METRICS_ENABLED;

  @Option(
      names = {METRICS_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Metrics port to expose metrics for Prometheus",
      arity = "1")
  private int metricsPort = DEFAULT_METRICS_PORT;

  @Option(
      names = {METRICS_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Metrics network interface to expose metrics for Prometheus",
      arity = "1")
  private String metricsInterface = DEFAULT_METRICS_INTERFACE;

  @Option(
      names = {METRICS_CATEGORIES_OPTION_NAME},
      paramLabel = "<METRICS_CATEGORY>",
      description = "Metric categories to enable",
      split = ",",
      arity = "0..*")
  private ArrayList<String> metricsCategories = DEFAULT_METRICS_CATEGORIES;

  // Database

  @Option(
      names = {DATA_PATH_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path to output data files",
      arity = "1")
  private String dataPath = DEFAULT_DATA_PATH;

  @Option(
      names = {DATA_STORAGE_MODE_OPTION_NAME},
      paramLabel = "<STORAGE_MODE>",
      description =
          "Sets the strategy for handling historical chain state.  Supported values include: 'prune', and 'archive'",
      arity = "1")
  private String dataStorageMode = DEFAULT_DATA_STORAGE_MODE;

  // Beacon REST API

  @Option(
      names = {REST_API_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Port number of Beacon Rest API",
      arity = "1")
  private int restApiPort = DEFAULT_REST_API_PORT;

  @Option(
      names = {REST_API_DOCS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enable swagger-docs and swagger-ui endpoints",
      arity = "1")
  private boolean restApiDocsEnabled = DEFAULT_REST_API_DOCS_ENABLED;

  @Option(
      names = {REST_API_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables Beacon Rest API",
      arity = "1")
  private boolean restApiEnabled = DEFAULT_REST_API_ENABLED;

  @Option(
      names = {REST_API_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Interface of Beacon Rest API",
      arity = "1")
  private String restApiInterface = DEFAULT_REST_API_INTERFACE;

  private ArtemisConfiguration artemisConfiguration;
  private BeaconNode node;

  public String getConfigFile() {
    return configFile;
  }

  public void parse(final String[] args) {
    final CommandLine commandLine = new CommandLine(this).setCaseInsensitiveEnumValuesAllowed(true);

    final Optional<File> maybeConfigFile = maybeFindConfigFile(commandLine, args);
    if (maybeConfigFile.isPresent()) {
      final CommandLine.IDefaultValueProvider defaultValueProvider =
          new CascadingDefaultProvider(
              new TomlConfigFileDefaultProvider(commandLine, maybeConfigFile.get()));
      commandLine.setDefaultValueProvider(defaultValueProvider);
    }
    commandLine.execute(args);
  }

  @Override
  public Integer call() {
    try {
      setLogLevels();
      artemisConfiguration = artemisConfiguration();
      node = new BeaconNode(artemisConfiguration);
      node.start();
      // Detect SIGTERM
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("Teku is shutting down");
                    node.stop();
                  }));
      return 0;
    } catch (DatabaseStorageException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (Throwable t) {
      System.err.println("Teku failed to start.");
      t.printStackTrace();
      System.exit(1);
    }
    return 1;
  }

  @VisibleForTesting
  ArtemisConfiguration getArtemisConfiguration() {
    return artemisConfiguration;
  }

  @VisibleForTesting
  public void stop() {
    node.stop();
  }

  private Optional<File> maybeFindConfigFile(final CommandLine commandLine, final String[] args) {
    final CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
    if (parseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)) {
      final CommandLine.Model.OptionSpec configFileOption =
          parseResult.matchedOption(CONFIG_FILE_OPTION_NAME);
      try {
        return Optional.of(new File(configFileOption.getter().get().toString()));
      } catch (final Exception e) {
        throw new CommandLine.ExecutionException(commandLine, e.getMessage(), e);
      }
    } else {
      return Optional.empty();
    }
  }

  private void setLogLevels() {
    if (logLevel != null) {
      // set log level per CLI flags
      LoggingConfigurator.setAllLevels(logLevel);
    }
  }

  private ArtemisConfiguration artemisConfiguration() {
    // TODO: validate option dependencies
    return ArtemisConfiguration.builder()
        .setNetwork(network)
        .setP2pEnabled(p2pEnabled)
        .setP2pInterface(p2pInterface)
        .setP2pPort(p2pPort)
        .setP2pDiscoveryEnabled(p2pDiscoveryEnabled)
        .setP2pDiscoveryBootnodes(p2pDiscoveryBootnodes)
        .setP2pAdvertisedIp(p2pAdvertisedIp)
        .setP2pAdvertisedPort(p2pAdvertisedPort)
        .setP2pPrivateKeyFile(p2pPrivateKeyFile)
        .setP2pPeerLowerBound(p2pLowerBound)
        .setP2pPeerUpperBound(p2pUpperBound)
        .setP2pStaticPeers(p2pStaticPeers)
        .setxInteropGenesisTime(xInteropGenesisTime)
        .setxInteropOwnedValidatorStartIndex(xInteropOwnerValidatorStartIndex)
        .setxInteropOwnedValidatorCount(xInteropOwnerValidatorCount)
        .setxInteropStartState(xInteropStartState)
        .setxInteropNumberOfValidators(xInteropNumberOfValidators)
        .setxInteropEnabled(xInteropEnabled)
        .setValidatorsKeyFile(validatorsKeyFile)
        .setValidatorsKeystoreFiles(validatorsKeystoreFiles)
        .setValidatorsKeystorePasswordFiles(validatorsKeystorePasswordFiles)
        .setEth1DepositContractAddress(eth1DepositContractAddress)
        .setEth1Endpoint(eth1Endpoint)
        .setLogColourEnabled(logColourEnabled)
        .setLogIncludeEventsEnabled(logIncludeEventsEnabled)
        .setLogDestination(logDestination)
        .setLogFile(logFile)
        .setLogFileNamePattern(logFileNamePattern)
        .setxTransitionRecordDirectory(xTransitionRecordDirectory)
        .setMetricsEnabled(metricsEnabled)
        .setMetricsPort(metricsPort)
        .setMetricsInterface(metricsInterface)
        .setMetricsCategories(metricsCategories)
        .setDataPath(dataPath)
        .setDataStorageMode(dataStorageMode)
        .setRestApiPort(restApiPort)
        .setRestApiDocsEnabled(restApiDocsEnabled)
        .setRestApiEnabled(restApiEnabled)
        .setRestApiInterface(restApiInterface)
        .build();
  }
}
