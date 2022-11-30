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

import com.google.common.io.Resources;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class MergedGenesisAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "less-swift";
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");
  public static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

  private BesuNode eth1Node;
  private TekuNode tekuNode;

  @BeforeEach
  void setup() throws Exception {
    eth1Node =
        createBesuNode(
            config ->
                config
                    .withMergeSupport(true)
                    .withGenesisFile("besu/mergedGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    eth1Node.start();

    final ValidatorKeystores validatorKeys =
        createTekuDepositSender(NETWORK_NAME).generateValidatorKeys(4, WITHDRAWAL_ADDRESS);
    final InitialStateData initialStateData =
        createGenesisGenerator()
            .network(NETWORK_NAME)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withTotalTerminalDifficulty(0)
            .genesisPayloadSource(eth1Node)
            .validatorKeys(validatorKeys)
            .generate();
    tekuNode =
        createTekuNode(
            config ->
                config
                    .withNetwork(NETWORK_NAME)
                    .withAltairEpoch(UInt64.ZERO)
                    .withBellatrixEpoch(UInt64.ZERO)
                    .withTotalTerminalDifficulty(0)
                    .withInitialState(initialStateData)
                    .withStartupTargetPeerCount(0)
                    .withValidatorKeystores(validatorKeys)
                    .withValidatorProposerDefaultFeeRecipient(
                        "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
                    .withExecutionEngine(eth1Node)
                    .withJwtSecretFile(JWT_FILE));
    tekuNode.start();
  }

  @Test
  void shouldHaveNonDefaultExecutionPayloadAndFinalizeAfterMergeTransition() {
    tekuNode.waitForGenesisWithNonDefaultExecutionPayload();
    tekuNode.waitForNewFinalization();
  }
}
