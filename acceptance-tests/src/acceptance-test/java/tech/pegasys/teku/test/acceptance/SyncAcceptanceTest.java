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
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;

public class SyncAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldSyncToNodeWithGreaterFinalizedEpoch() throws Exception {
    final TekuNode primaryNode = createTekuNode(Config::withRealNetwork);

    primaryNode.start();
    UInt64 genesisTime = primaryNode.getGenesisTime();
    final TekuNode lateJoiningNode =
        createTekuNode(configureLateJoiningNode(primaryNode, genesisTime.intValue()));
    primaryNode.waitForNewFinalization();

    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
  }

  private Consumer<Config> configureLateJoiningNode(
      final TekuNode primaryNode, final int genesisTime) {
    return c ->
        c.withGenesisTime(genesisTime)
            .withRealNetwork()
            .withPeers(primaryNode)
            .withInteropValidators(0, 0);
  }
}
