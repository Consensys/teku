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

package tech.pegasys.artemis.util.config;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.config.Configuration;
import org.apache.tuweni.config.PropertyValidator;
import org.apache.tuweni.config.Schema;
import org.apache.tuweni.config.SchemaBuilder;
import org.apache.tuweni.crypto.SECP256K1;

/** Configuration of an instance of Artemis. */
public class ArtemisConfiguration {

  @SuppressWarnings({"DoubleBraceInitialization"})
  static final Schema createSchema() {
    SchemaBuilder builder =
        SchemaBuilder.create()
            .addString(
                "node.networkMode",
                "mock",
                "represents what network to use",
                PropertyValidator.anyOf("mock", "jvmlibp2p"));

    builder.addString("node.identity", null, "Identity of the peer", null);
    builder.addString("node.timer", "QuartzTimer", "Timer used for slots", null);
    builder.addString("node.networkInterface", "0.0.0.0", "Peer to peer network interface", null);
    builder.addInteger("node.port", 9000, "Peer to peer port", PropertyValidator.inRange(0, 65535));
    builder.addInteger(
        "node.advertisedPort",
        9000,
        "Peer to peer advertised port",
        PropertyValidator.inRange(0, 65535));
    builder.addString("node.discovery", "", "static or discv5", null);
    builder.addString("node.bootnodes", "", "ENR of the bootnode", null);
    builder.addBoolean("node.isBootnode", true, "Makes this node a bootnode", null);
    builder.addInteger(
        "node.naughtinessPercentage",
        0,
        "Percentage of Validator Clients that are naughty",
        PropertyValidator.inRange(0, 101));
    builder.addString(
        "validator.validatorsKeyFile", "", "The file to load validator keys from", null);
    builder.addInteger(
        "deposit.numValidators",
        128,
        "represents the total number of validators in the network",
        PropertyValidator.inRange(1, 65535));
    builder.addInteger(
        "deposit.numNodes",
        1,
        "represents the total number of nodes on the network",
        PropertyValidator.inRange(1, 65535));
    builder.addString("deposit.mode", "normal", "PoW Deposit Mode", null);
    builder.addString("deposit.inputFile", "", "PoW simulation optional input file", null);
    builder.addString("deposit.nodeUrl", null, "URL for Eth 1.0 node", null);
    builder.addString(
        "deposit.contractAddr", null, "Contract address for the deposit contract", null);
    builder.addListOfString("node.peers", Collections.emptyList(), "Static peers", null);
    builder.addLong(
        "node.networkID", 1L, "The identifier of the network (mainnet, testnet, sidechain)", null);
    builder.addString(
        "node.constants",
        "minimal",
        "Determines whether to use minimal or mainnet constants",
        null);

    // Interop
    builder.addBoolean("interop.active", false, "Enable interop mode", null);
    builder.addLong("interop.genesisTime", null, "Time of mocked genesis", null);
    builder.addInteger(
        "interop.ownedValidatorStartIndex", 0, "Index of first validator owned by this node", null);
    builder.addInteger(
        "interop.ownedValidatorCount", 0, "Number of validators owned by this node", null);
    builder.addString("interop.startState", "", "Initial BeaconState to load", null);
    builder.addString("interop.privateKey", "", "This node's private key", null);

    // Metrics
    builder.addBoolean("metrics.enabled", false, "Enables metrics collection via Prometheus", null);
    builder.addString(
        "metrics.metricsNetworkInterface",
        "0.0.0.0",
        "Metrics network interface to expose metrics for Prometheus",
        null);
    builder.addInteger(
        "metrics.metricsPort",
        8008,
        "Metrics port to expose metrics for Prometheus",
        PropertyValidator.inRange(0, 65535));
    builder.addListOfString(
        "metrics.metricsCategories",
        asList("JVM", "PROCESS", "BEACONCHAIN"),
        "Metric categories to enable",
        null);
    // Outputs
    builder.addString(
        "output.logPath", ".", "Path to output the log file", PropertyValidator.isPresent());
    builder.addString(
        "output.logFile", "artemis.log", "Log file name", PropertyValidator.isPresent());
    builder.addString(
        "output.transitionRecordDir",
        "",
        "Directory to record transition pre and post states",
        null);

    // Artemis specific
    builder.addString("constants.SIM_DEPOSIT_VALUE", "", null, null);
    builder.addInteger("constants.DEPOSIT_DATA_SIZE", Integer.MIN_VALUE, null, null);

    builder.validateConfiguration(
        config -> {
          return null;
        });

    return builder.toSchema();
  }

  private static final Schema schema = createSchema();

  /**
   * Reads configuration from file.
   *
   * @param path a toml file to read configuration from
   * @return the new ArtemisConfiguration
   * @throws UncheckedIOException if the file is missing
   */
  public static ArtemisConfiguration fromFile(String path) {
    Path configPath = Paths.get(path);
    try {
      return new ArtemisConfiguration(Configuration.fromToml(configPath, schema));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads configuration from a toml text.
   *
   * @param configText the toml text
   * @return the new ArtemisConfiguration
   */
  public static ArtemisConfiguration fromString(String configText) {
    return new ArtemisConfiguration(Configuration.fromToml(configText, schema));
  }

  private final Configuration config;

  private ArtemisConfiguration(Configuration config) {
    this.config = config;
    if (config.hasErrors()) {
      throw new IllegalArgumentException(
          config.errors().stream()
              .map(error -> error.position() + " " + error.toString())
              .collect(Collectors.joining("\n")));
    }
  }

  /** @return the identity of the node, the hexadecimal representation of its secret key */
  public int getNaughtinessPercentage() {
    return config.getInteger("node.naughtinessPercentage");
  }

  /** @return the identity of the node, the hexadecimal representation of its secret key */
  public String getIdentity() {
    return config.getString("node.identity");
  }

  /** @return the identity of the node, the hexadecimal representation of its secret key */
  public String getTimer() {
    return config.getString("node.timer");
  }

  /** @return the port this node will listen to */
  public int getPort() {
    return config.getInteger("node.port");
  }

  public String getDiscovery() {
    return config.getString("node.discovery");
  }

  public boolean isBootnode() {
    return config.getBoolean("node.isBootnode");
  }

  public String getBootnodes() {
    return config.getString("node.bootnodes");
  }

  /** @return the port this node will advertise as its own */
  public int getAdvertisedPort() {
    return config.getInteger("node.advertisedPort");
  }

  /** @return the network interface this node will bind to */
  public String getNetworkInterface() {
    return config.getString("node.networkInterface");
  }

  public String getConstants() {
    return config.getString("node.constants");
  }

  /** @return the total number of validators in the network */
  public int getNumValidators() {
    return config.getInteger("deposit.numValidators");
  }

  public boolean getInteropActive() {
    return config.getBoolean("interop.active");
  }

  public String getInteropStartState() {
    final String startState = config.getString("interop.startState");
    return startState == null || startState.isEmpty() ? null : startState;
  }

  public long getGenesisTime() {
    return config.getLong("interop.genesisTime");
  }

  public int getInteropOwnedValidatorStartIndex() {
    return config.getInteger("interop.ownedValidatorStartIndex");
  }

  public int getInteropOwnedValidatorCount() {
    return config.getInteger("interop.ownedValidatorCount");
  }

  public String getInteropPrivateKey() {
    return config.getString("interop.privateKey");
  }

  /** @return the total number of nodes on the network */
  public int getNumNodes() {
    return config.getInteger("deposit.numNodes");
  }

  public String getValidatorsKeyFile() {
    final String keyFile = config.getString("validator.validatorsKeyFile");
    return keyFile == null || keyFile.isEmpty() ? null : keyFile;
  }

  /** @return the Deposit simulation flag, w/ optional input file */
  public String getInputFile() {
    String inputFile = config.getString("deposit.inputFile");
    if (inputFile == null || inputFile.equals("")) return null;
    return inputFile;
  }

  public String getContractAddr() {
    return config.getString("deposit.contractAddr");
  }

  public String getNodeUrl() {
    return config.getString("deposit.nodeUrl");
  }

  /** @return if simulation is enabled or not */
  public String getDepositMode() {
    return config.getString("deposit.mode");
  }

  /** @return the Output provider types: CSV, JSON */
  public String getProviderType() {
    return config.getString("output.providerType");
  }

  /** @return if metrics is enabled or not */
  public Boolean isMetricsEnabled() {
    return config.getBoolean("metrics.enabled");
  }

  public String getMetricsNetworkInterface() {
    return config.getString("metrics.metricsNetworkInterface");
  }

  public int getMetricsPort() {
    return config.getInteger("metrics.metricsPort");
  }

  public List<String> getMetricCategories() {
    return config.getListOfString("metrics.metricsCategories");
  }

  public String getTransitionRecordDir() {
    return config.getString("output.transitionRecordDir");
  }

  /** @return Artemis specific constants */
  public List<String> getStaticPeers() {
    return config.getListOfString("node.peers");
  }

  /** @return the identity key pair of the node */
  public SECP256K1.KeyPair getKeyPair() {
    return SECP256K1.KeyPair.fromSecretKey(
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(getIdentity())));
  }

  /** @return the identifier of the network (mainnet, testnet, sidechain) */
  public long getNetworkID() {
    return config.getLong("node.networkID");
  }

  /** @return the mode of the network to use - mock or libp2p */
  public String getNetworkMode() {
    return config.getString("node.networkMode");
  }

  /** @return the path to the log file */
  public String getLogPath() {
    return config.getString("output.logPath");
  }

  /** @return the name of the log file */
  public String getLogFile() {
    return config.getString("output.logFile");
  }
}
