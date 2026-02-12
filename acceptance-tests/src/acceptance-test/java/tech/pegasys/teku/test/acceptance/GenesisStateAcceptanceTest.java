/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder.DEFAULT_NETWORK_NAME;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuDepositSender;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

@Disabled
public class GenesisStateAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldCreateTheSameGenesisState() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    createTekuDepositSender(DEFAULT_NETWORK_NAME).sendValidatorDeposits(eth1Node, 4);

    final TekuBeaconNode firstTeku =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withDepositsFrom(eth1Node).build());
    firstTeku.start();
    firstTeku.waitForGenesis();

    final TekuBeaconNode lateJoinTeku =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withDepositsFrom(eth1Node).build());

    lateJoinTeku.start();
    lateJoinTeku.waitForGenesis();

    // Even though the nodes aren't connected to each other they should generate the same genesis
    // state because they processed the same deposits from the same ETH1 chain.
    lateJoinTeku.waitUntilInSyncWith(firstTeku);
  }

  @Test
  public void shouldCreateGenesisFromPartialDeposits() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();
    int numberOfValidators = 4;

    final TekuDepositSender depositSender = createTekuDepositSender(DEFAULT_NETWORK_NAME);
    final ValidatorKeystores validatorKeys =
        depositSender.generateValidatorKeys(numberOfValidators);
    depositSender.sendValidatorDeposits(
        eth1Node, validatorKeys, depositSender.getMinDepositAmount());
    depositSender.sendValidatorDeposits(
        eth1Node,
        validatorKeys,
        depositSender.getMaxEffectiveBalance().minus(depositSender.getMinDepositAmount()));

    final TekuBeaconNode teku =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withDepositsFrom(eth1Node).build());

    teku.start();
    teku.waitForGenesis();

    teku.waitForValidators(numberOfValidators);
  }
}
