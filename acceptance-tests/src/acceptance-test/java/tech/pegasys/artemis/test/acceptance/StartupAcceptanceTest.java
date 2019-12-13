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

import java.io.File;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode;
import tech.pegasys.artemis.test.acceptance.dsl.BesuNode;

public class StartupAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldProgressChainAfterStartingFromMockGenesis() throws Exception {
    final ArtemisNode node = createArtemisNode();
    node.start();
    node.waitForGenesis();
    node.waitForNewBlock();
  }

  @Disabled
  @Test
  public void shouldProgressChainAfterStartingFromDisk() throws Exception {
    final ArtemisNode node1 = createArtemisNode();
    node1.start();
    node1.waitForGenesis();
    node1.waitForNewFinalization();
    File tempDatabaseFile = node1.getDatabaseFileFromContainer();
    node1.stop();

    final ArtemisNode node2 = createArtemisNode(ArtemisNode.Config::startFromDisk);
    node2.copyDatabaseFileToContainer(tempDatabaseFile);
    node2.start();
    node1.waitForGenesis();
    node2.waitForNewFinalization();
    node2.stop();
  }

  @Test
  public void shouldStartChainFromDepositContract() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final ArtemisNode artemisNode = createArtemisNode(config -> config.withDepositsFrom(eth1Node));
    artemisNode.start();

    createArtemisDepositSender().sendValidatorDeposits(eth1Node, 64);
    artemisNode.waitForGenesis();
  }
}
