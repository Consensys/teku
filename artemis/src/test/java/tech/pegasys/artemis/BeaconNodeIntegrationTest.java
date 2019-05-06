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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
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
  void testThreeNodes() throws InterruptedException, JsonProcessingException, IOException {
    CommandLineArguments cliArgs = new CommandLineArguments();
    CommandLine commandLine = new CommandLine(cliArgs);
    commandLine.parse("");

    // Read all lines from a file
    String content = "";
    content =
        new String(Files.readAllBytes(Paths.get("../config/config.toml")), StandardCharsets.UTF_8);

    // Construct the configuration file for the first node
    String updatedConfig1 =
        content
            .replaceFirst("networkMode = \"mock\"", "networkMode = \"hobbits\"")
            .replaceFirst("networkInterface = \"0.0.0.0\"", "networkInterface = \"127.0.0.1\"")
            .replaceFirst(
                "port = 9000", "port = 19000\npeers = [\"hob+tcp://abcf@localhost:19001\"]")
            .replaceFirst("advertisedPort = 9000", "advertisedPort = 19000")
            .replaceFirst("numNodes = 1", "numNodes = 2");

    // Construct the configuration file for the second node
    String updatedConfig2 =
        content
            .replaceFirst("networkMode = \"mock\"", "networkMode = \"hobbits\"")
            .replaceFirst("identity = \"0x00\"", "identity = \"0x01\"")
            .replaceFirst("networkInterface = \"0.0.0.0\"", "networkInterface = \"127.0.0.1\"")
            .replaceFirst(
                "port = 9000", "port = 19001\npeers = [\"hob+tcp://abcf@localhost:19000\"]")
            .replaceFirst("advertisedPort = 9000", "advertisedPort = 19001")
            .replaceFirst("numNodes = 1", "numNodes = 2");

    ArtemisConfiguration config1 = ArtemisConfiguration.fromString(updatedConfig1);
    ArtemisConfiguration config2 = ArtemisConfiguration.fromString(updatedConfig2);

    BeaconNode node1 = new BeaconNode(commandLine, cliArgs, config1);
    BeaconNode node2 = new BeaconNode(commandLine, cliArgs, config2);

    node1.start();
    node2.start();

    Thread.sleep(10000);

    P2PNetwork net1 = node1.p2pNetwork();
    P2PNetwork net2 = node2.p2pNetwork();

    Bytes block = null;
    for (P2PNetwork net : Arrays.asList(net1, net2)) {
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
  }
}
