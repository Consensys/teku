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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import picocli.CommandLine;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.hobbits.Peer;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

@ExtendWith(BouncyCastleExtension.class)
class BeaconNodeIntegrationTest {

  private static ObjectMapper mapper = new ObjectMapper();

  @Test
  void testThreeNodes() throws InterruptedException, JsonProcessingException {
    CommandLineArguments cliArgs = new CommandLineArguments();
    CommandLine commandLine = new CommandLine(cliArgs);

    ArtemisConfiguration config1 = ArtemisConfiguration.fromFile("../config/testConfig.0.toml");
    ArtemisConfiguration config2 = ArtemisConfiguration.fromFile("../config/testConfig.1.toml");
    ArtemisConfiguration config3 = ArtemisConfiguration.fromFile("../config/testConfig.2.toml");

    BeaconNode node1 = new BeaconNode(commandLine, cliArgs, config1);
    BeaconNode node2 = new BeaconNode(commandLine, cliArgs, config2);
    BeaconNode node3 = new BeaconNode(commandLine, cliArgs, config3);

    node1.start();
    node2.start();
    node3.start();

    Thread.sleep(10000);

    P2PNetwork net1 = node1.p2pNetwork();
    P2PNetwork net2 = node2.p2pNetwork();
    P2PNetwork net3 = node3.p2pNetwork();

    Bytes block = null;
    for (P2PNetwork net : Arrays.asList(net1, net2, net3)) {
      for (Object p : net.getPeers()) {
        Peer peer = (Peer) p;
        Bytes message = peer.peerGossip();
        if (!Objects.isNull(message)) {
          block = message;
          break;
        }
      }
    }
    assertNotNull(block);
    node1.stop();
    node2.stop();
    node3.stop();
  }

  @Test
  void testThreeNodesWithRLPx() throws InterruptedException, JsonProcessingException {
    CommandLineArguments cliArgs = new CommandLineArguments();
    CommandLine commandLine = new CommandLine(cliArgs);

    ArtemisConfiguration config1 = ArtemisConfiguration.fromFile("../config/rlpxConfig.0.toml");
    ArtemisConfiguration config2 = ArtemisConfiguration.fromFile("../config/rlpxConfig.1.toml");
    ArtemisConfiguration config3 = ArtemisConfiguration.fromFile("../config/rlpxConfig.2.toml");

    BeaconNode node1 = new BeaconNode(commandLine, cliArgs, config1);
    BeaconNode node2 = new BeaconNode(commandLine, cliArgs, config2);
    BeaconNode node3 = new BeaconNode(commandLine, cliArgs, config3);

    node2.start();
    node3.start();
    node1.start();

    Thread.sleep(20000);

    P2PNetwork net1 = node1.p2pNetwork();
    P2PNetwork net2 = node2.p2pNetwork();
    P2PNetwork net3 = node3.p2pNetwork();

    Bytes block = null;
    for (P2PNetwork net : Arrays.asList(net1, net2, net3)) {
      for (Object p : net.getPeers()) {
        Peer peer = (Peer) p;
        Bytes message = peer.peerGossip();
        if (!Objects.isNull(message)) {
          block = message;
          break;
        }
      }
    }
    assertNotNull(block);
    node1.stop();
    node2.stop();
    node3.stop();
  }
}
