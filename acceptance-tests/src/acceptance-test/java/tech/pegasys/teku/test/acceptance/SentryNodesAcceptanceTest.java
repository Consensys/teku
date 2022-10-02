/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.test.acceptance;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.SentryNodesConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class SentryNodesAcceptanceTest extends AcceptanceTestBase {

  @Test
  void sentryBeaconNodesSetup() throws Exception {
    final TekuNode dutiesProviderNode = createAndStartBootstrapBeaconNode();
    final TekuNode attestationPublisherNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());
    final TekuNode blockHandlerNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());

    final SentryNodesConfig sentryNodesConfig =
        new SentryNodesConfig.Builder()
            .withDutiesProviders(dutiesProviderNode)
            .withAttestationPublisher(attestationPublisherNode)
            .withBlockHandlers(blockHandlerNode)
            .build();

    final TekuValidatorNode remoteValidator =
        createValidatorNode(
            config -> config.withInteropValidators(0, 32).withSentryNodes(sentryNodesConfig));
    remoteValidator.start();

    remoteValidator.waitForDutiesRequestedFrom(dutiesProviderNode);
    remoteValidator.waitForAttestationPublishedTo(attestationPublisherNode);
    remoteValidator.waitForBlockPublishedTo(blockHandlerNode);
  }

  private TekuNode createAndStartPeerBeaconNode(
      final TekuNode dutiesProviderNode, final UInt64 genesisTime) throws Exception {
    final TekuNode blockHandlerNode =
        createTekuNode(configureLateJoiningNode(dutiesProviderNode, genesisTime.intValue()));
    blockHandlerNode.start();
    return blockHandlerNode;
  }

  private TekuNode createAndStartBootstrapBeaconNode() throws Exception {
    final TekuNode dutiesProviderNode =
        createTekuNode(
            c -> {
              c.withRealNetwork();
              c.withNetwork("minimal");
              c.withInteropNumberOfValidators(64);
              c.withInteropValidators(32, 32);
            });
    dutiesProviderNode.start();
    return dutiesProviderNode;
  }

  private Consumer<Config> configureLateJoiningNode(
      final TekuNode primaryNode, final int genesisTime) {
    return c ->
        c.withGenesisTime(genesisTime)
            .withRealNetwork()
            .withNetwork("minimal")
            .withPeers(primaryNode)
            .withInteropValidators(0, 0);
  }
}
