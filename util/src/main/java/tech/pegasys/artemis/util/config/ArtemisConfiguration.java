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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.config.Configuration;
import net.consensys.cava.config.ConfigurationError;
import net.consensys.cava.config.PropertyValidator;
import net.consensys.cava.config.Schema;
import net.consensys.cava.config.SchemaBuilder;
import net.consensys.cava.crypto.SECP256K1;

/** Configuration of an instance of Artemis. */
public final class ArtemisConfiguration {

  static final Schema createSchema() {
    SchemaBuilder builder =
        SchemaBuilder.create()
            .addString(
                "node.networkMode",
                "mock",
                "represents what network to use",
                PropertyValidator.anyOf("mock", "rlpx", "hobbits"));
    builder.addString("node.identity", null, "Identity of the peer", PropertyValidator.isPresent());
    builder.addString("node.networkInterface", "0.0.0.0", "Peer to peer network interface", null);
    builder.addInteger("node.port", 9000, "Peer to peer port", PropertyValidator.inRange(0, 65535));
    builder.addInteger(
        "node.advertisedPort",
        9000,
        "Peer to peer advertised port",
        PropertyValidator.inRange(0, 65535));
    builder.addInteger(
        "sim.numValidators",
        128,
        "represents the total number of validators in the network",
        PropertyValidator.inRange(1, 16384));
    builder.addInteger(
        "sim.numNodes",
        1,
        "represents the total number of nodes on the network",
        PropertyValidator.inRange(1, 16384));
    builder.addListOfString(
        "node.peers",
        Collections.emptyList(),
        "Static peers",
        (key, position, peers) ->
            peers.stream()
                .map(
                    peer -> {
                      try {
                        URI uri = new URI(peer);
                        String userInfo = uri.getUserInfo();
                        if (userInfo == null || userInfo.isEmpty()) {
                          return new ConfigurationError("Missing public key");
                        }
                      } catch (URISyntaxException e) {
                        return new ConfigurationError("Invalid uri " + peer);
                      }
                      return null;
                    })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    builder.addLong(
        "node.networkID", 1L, "The identifier of the network (mainnet, testnet, sidechain)", null);
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
  public String getIdentity() {
    return config.getString("node.identity");
  }

  /** @return the port this node will listen to */
  public int getPort() {
    return config.getInteger("node.port");
  }

  /** @return the port this node will advertise as its own */
  public int getAdvertisedPort() {
    return config.getInteger("node.advertisedPort");
  }

  /** @return the network interface this node will bind to */
  public String getNetworkInterface() {
    return config.getString("node.networkInterface");
  }

  /** @return the total number of validators in the network */
  public int getNumValidators() {
    return config.getInteger("sim.numValidators");
  }

  /** @return the total number of nodes on the network */
  public int getNumNodes() {
    return config.getInteger("sim.numNodes");
  }

  /** @return the list of static peers associated with this node */
  public List<URI> getStaticPeers() {
    return config.getListOfString("node.peers").stream()
        .map(
            (peer) -> {
              try {
                return new URI(peer);
              } catch (URISyntaxException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
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

  /** @return the mode of the network to use - mock, rlpx or hobbits */
  public String getNetworkMode() {
    return config.getString("node.networkMode");
  }
}
