/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.cli.options;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

public class P2POptions {

  public static final String P2P_ENABLED_OPTION_NAME = "--p2p-enabled";
  public static final String P2P_INTERFACE_OPTION_NAME = "--p2p-interface";
  public static final String P2P_PORT_OPTION_NAME = "--p2p-port";
  public static final String P2P_DISCOVERY_ENABLED_OPTION_NAME = "--p2p-discovery-enabled";
  public static final String P2P_DISCOVERY_BOOTNODES_OPTION_NAME = "--p2p-discovery-bootnodes";
  public static final String P2P_ADVERTISED_IP_OPTION_NAME = "--p2p-advertised-ip";
  public static final String P2P_ADVERTISED_PORT_OPTION_NAME = "--p2p-advertised-port";
  public static final String P2P_PRIVATE_KEY_FILE_OPTION_NAME = "--p2p-private-key-file";
  public static final String P2P_PEER_LOWER_BOUND_OPTION_NAME = "--p2p-peer-lower-bound";
  public static final String P2P_PEER_UPPER_BOUND_OPTION_NAME = "--p2p-peer-upper-bound";
  public static final String P2P_STATIC_PEERS_OPTION_NAME = "--p2p-static-peers";

  public static final boolean DEFAULT_P2P_ENABLED = true;
  public static final String DEFAULT_P2P_INTERFACE = "0.0.0.0";
  public static final int DEFAULT_P2P_PORT = 30303;
  public static final boolean DEFAULT_P2P_DISCOVERY_ENABLED = true;
  public static final List<String> DEFAULT_P2P_DISCOVERY_BOOTNODES =
      null; // depends on network option
  public static final String DEFAULT_P2P_ADVERTISED_IP = "127.0.0.1";
  public static final int DEFAULT_P2P_ADVERTISED_PORT = DEFAULT_P2P_PORT;
  public static final String DEFAULT_P2P_PRIVATE_KEY_FILE = null;
  public static final int DEFAULT_P2P_PEER_LOWER_BOUND = 20;
  public static final int DEFAULT_P2P_PEER_UPPER_BOUND = 30;
  public static final List<String> DEFAULT_P2P_STATIC_PEERS = new ArrayList<>();

  @CommandLine.Option(
      names = {P2P_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables peer to peer",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pEnabled = DEFAULT_P2P_ENABLED;

  @CommandLine.Option(
      names = {P2P_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Peer to peer network interface",
      arity = "1")
  private String p2pInterface = DEFAULT_P2P_INTERFACE;

  @CommandLine.Option(
      names = {P2P_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Peer to peer port",
      arity = "1")
  private int p2pPort = DEFAULT_P2P_PORT;

  @CommandLine.Option(
      names = {P2P_DISCOVERY_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables discv5 discovery",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pDiscoveryEnabled = DEFAULT_P2P_DISCOVERY_ENABLED;

  @CommandLine.Option(
      names = {P2P_DISCOVERY_BOOTNODES_OPTION_NAME},
      paramLabel = "<enode://id@host:port>",
      description = "ENR of the bootnode",
      split = ",",
      arity = "0..*")
  private List<String> p2pDiscoveryBootnodes = DEFAULT_P2P_DISCOVERY_BOOTNODES;

  @CommandLine.Option(
      names = {P2P_ADVERTISED_IP_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Peer to peer advertised ip",
      arity = "1")
  private String p2pAdvertisedIp = DEFAULT_P2P_ADVERTISED_IP;

  @CommandLine.Option(
      names = {P2P_ADVERTISED_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Peer to peer advertised port",
      arity = "1")
  private int p2pAdvertisedPort = DEFAULT_P2P_ADVERTISED_PORT;

  @CommandLine.Option(
      names = {P2P_PRIVATE_KEY_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "This node's private key file",
      arity = "1")
  private String p2pPrivateKeyFile = DEFAULT_P2P_PRIVATE_KEY_FILE;

  @CommandLine.Option(
      names = {P2P_PEER_LOWER_BOUND_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      arity = "1")
  private int p2pLowerBound = DEFAULT_P2P_PEER_LOWER_BOUND;

  @CommandLine.Option(
      names = {P2P_PEER_UPPER_BOUND_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      arity = "1")
  private int p2pUpperBound = DEFAULT_P2P_PEER_UPPER_BOUND;

  @CommandLine.Option(
      names = {P2P_STATIC_PEERS_OPTION_NAME},
      paramLabel = "<PEER_ADDRESSES>",
      description = "Static peers",
      split = ",",
      arity = "0..*")
  private List<String> p2pStaticPeers = DEFAULT_P2P_STATIC_PEERS;

  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public String getP2pInterface() {
    return p2pInterface;
  }

  public int getP2pPort() {
    return p2pPort;
  }

  public boolean isP2pDiscoveryEnabled() {
    return p2pDiscoveryEnabled;
  }

  public List<String> getP2pDiscoveryBootnodes() {
    return p2pDiscoveryBootnodes;
  }

  public String getP2pAdvertisedIp() {
    return p2pAdvertisedIp;
  }

  public int getP2pAdvertisedPort() {
    return p2pAdvertisedPort;
  }

  public String getP2pPrivateKeyFile() {
    return p2pPrivateKeyFile;
  }

  public int getP2pLowerBound() {
    return p2pLowerBound;
  }

  public int getP2pUpperBound() {
    return p2pUpperBound;
  }

  public List<String> getP2pStaticPeers() {
    return p2pStaticPeers;
  }
}
