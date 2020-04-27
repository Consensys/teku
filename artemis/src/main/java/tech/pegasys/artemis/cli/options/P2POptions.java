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

import static java.util.Collections.emptyList;

import java.util.List;
import picocli.CommandLine.Option;

public class P2POptions {

  @Option(
      names = {"--p2p-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables peer to peer",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pEnabled = true;

  @Option(
      names = {"--p2p-interface"},
      paramLabel = "<NETWORK>",
      description = "Peer to peer network interface",
      arity = "1")
  private String p2pInterface = "0.0.0.0";

  @Option(
      names = {"--p2p-port"},
      paramLabel = "<INTEGER>",
      description = "Peer to peer port",
      arity = "1")
  private int p2pPort = 30303;

  @Option(
      names = {"--p2p-discovery-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables discv5 discovery",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pDiscoveryEnabled = true;

  @Option(
      names = {"--p2p-discovery-bootnodes"},
      paramLabel = "<enode://id@host:port>",
      description = "ENR of the bootnode",
      split = ",",
      arity = "0..*")
  private List<String> p2pDiscoveryBootnodes = null;

  @Option(
      names = {"--p2p-advertised-ip"},
      paramLabel = "<NETWORK>",
      description = "Peer to peer advertised ip",
      arity = "1")
  private String p2pAdvertisedIp = "127.0.0.1";

  @Option(
      names = {"--p2p-advertised-port"},
      paramLabel = "<INTEGER>",
      description = "Peer to peer advertised port",
      arity = "1")
  private int p2pAdvertisedPort = p2pPort;

  @Option(
      names = {"--p2p-private-key-file"},
      paramLabel = "<FILENAME>",
      description = "This node's private key file",
      arity = "1")
  private String p2pPrivateKeyFile = null;

  @Option(
      names = {"--p2p-peer-lower-bound"},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      arity = "1")
  private int p2pLowerBound = 20;

  @Option(
      names = {"--p2p-peer-upper-bound"},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      arity = "1")
  private int p2pUpperBound = 30;

  @Option(
      names = {"--p2p-static-peers"},
      paramLabel = "<PEER_ADDRESSES>",
      description = "Static peers",
      split = ",",
      arity = "0..*")
  private List<String> p2pStaticPeers = emptyList();

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
