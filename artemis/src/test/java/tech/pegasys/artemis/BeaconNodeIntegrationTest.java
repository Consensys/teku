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
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
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

  @Disabled
  @Test
  void testThreeNodes() throws InterruptedException, JsonProcessingException {
    CommandLineArguments cliArgs = new CommandLineArguments();
    CommandLine commandLine = new CommandLine(cliArgs);

    Bytes32 randomId = Bytes32.random();

    ArtemisConfiguration config1 =
        ArtemisConfiguration.fromString(
            "networkMode=\"hobbits\"\n"
                + "identity=\""
                + randomId.toHexString()
                + "\"\n"
                + "networkInterface=\"127.0.0.1\"\n"
                + "port=19000\n"
                + "peers=[\"hobs://abcf@localhost:19001\", \"hobs://abcf@localhost:19002\"]");

    ArtemisConfiguration config2 =
        ArtemisConfiguration.fromString(
            "networkMode=\"hobbits\"\n"
                + "identity=\""
                + randomId.toHexString()
                + "\"\n"
                + "networkInterface=\"127.0.0.1\"\n"
                + "port=19001\n"
                + "peers=[\"hobs://abcf@localhost:19000\", \"hobs://abcf@localhost:19002\"]");
    ArtemisConfiguration config3 =
        ArtemisConfiguration.fromString(
            "networkMode=\"hobbits\"\n"
                + "identity=\""
                + randomId.toHexString()
                + "\"\n"
                + "networkInterface=\"127.0.0.1\"\n"
                + "port=19002\n"
                + "peers=[\"hobs://abcf@localhost:19001\", \"hobs://abcf@localhost:19000\"]");

    BeaconNode node1 = new BeaconNode(commandLine, cliArgs, config1);
    BeaconNode node2 = new BeaconNode(commandLine, cliArgs, config2);
    BeaconNode node3 = new BeaconNode(commandLine, cliArgs, config3);

    node1.start();
    node2.start();
    node3.start();

    Thread.sleep(5000);

    P2PNetwork net1 = node1.p2pNetwork();
    P2PNetwork net2 = node2.p2pNetwork();
    P2PNetwork net3 = node3.p2pNetwork();

    for (P2PNetwork net : Arrays.asList(net1, net2, net3)) {
      for (Object p : net.getPeers()) {
        assertNotNull(((Peer) p).peerHello());
        assertNotNull(((Peer) p).peerStatus());
        System.out.println(mapper.writer().writeValueAsString(((Peer) p).peerStatus()));
      }
    }

    node1.stop();
    node2.stop();
    node3.stop();
  }
}
