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
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode.Config;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNodeUtils;

public class SyncAcceptanceTest extends AcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void test() throws Exception {
    LOG.info("Start test");
    final int validatorCount = 2;
    File genesisFile = ArtemisNodeUtils.generateGenesisState(validatorCount);
    LOG.info(
        "Created genesis file (size: {}): {}", genesisFile.length(), genesisFile.getAbsolutePath());

    final String genesisStateContainerPath = "/config/genesis-state.bin";
    final ArtemisNode primaryNode =
        createArtemisNode(configurePrimaryNode(genesisStateContainerPath, validatorCount));
    final ArtemisNode lateJoiningNode =
        createArtemisNode(configureLateJoiningNode(genesisStateContainerPath, primaryNode));
    // Copy genesis state to containers
    primaryNode.copyFileToContainer(genesisFile, genesisStateContainerPath);
    lateJoiningNode.copyFileToContainer(genesisFile, genesisStateContainerPath);

    LOG.info("Start first node");
    primaryNode.start();
    LOG.info("Wait for genesis");
    primaryNode.waitForGenesis();
    LOG.info("Wait for new block");
    primaryNode.waitForNewBlock();
    LOG.info("Wait for finalized block");
    primaryNode.waitForNewFinalization();
    LOG.info("First node has finalized block");

    LOG.info("Starting second node");
    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
  }

  private Consumer<Config> configurePrimaryNode(
      final String genesisStatePath, final int validatorCount) {
    return c ->
        c.withGenesisState(genesisStatePath)
            .withRealNetwork()
            .withInteropValidators(0, validatorCount);
  }

  private Consumer<Config> configureLateJoiningNode(
      final String genesisStatePath, final ArtemisNode primaryNode) {
    return c -> c.withGenesisState(genesisStatePath).withRealNetwork().withPeers(primaryNode);
  }
}
