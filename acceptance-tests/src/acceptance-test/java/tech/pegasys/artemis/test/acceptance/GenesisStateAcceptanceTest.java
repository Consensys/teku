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

package tech.pegasys.artemis.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode;
import tech.pegasys.artemis.test.acceptance.dsl.BesuNode;

public class GenesisStateAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldCreateTheSameGenesisState() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    createArtemisDepositSender().sendValidatorDeposits(eth1Node, 64);

    final ArtemisNode firstArtemis =
        createArtemisNode(
            config -> config.withDepositsFrom(eth1Node));
    firstArtemis.start();
    firstArtemis.waitForGenesis();

    final ArtemisNode lateJoinArtemis =
        createArtemisNode(config -> config.withDepositsFrom(eth1Node));
    lateJoinArtemis.start();
    lateJoinArtemis.waitForGenesis();

    // Even though the nodes aren't connected to each other they should generate the same genesis
    // state because they processed the same deposits from the same ETH1 chain.
    lateJoinArtemis.waitUntilInSyncWith(firstArtemis);
  }
}
