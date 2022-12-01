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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;

public class CapellaUpgradeAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldUpgradeToCapella() throws Exception {
    TekuNode primaryNode =
        createTekuNode(
            c -> {
              c.withRealNetwork();
              c.withStartupTargetPeerCount(0);
              applyMilestoneConfig(c);
            });

    primaryNode.start();
    primaryNode.waitForNewFinalization();
    primaryNode.waitForMilestone(SpecMilestone.CAPELLA);

    UInt64 genesisTime = primaryNode.getGenesisTime();

    TekuNode lateJoiningNode =
        createTekuNode(
            c -> {
              c.withGenesisTime(genesisTime.intValue());
              c.withRealNetwork();
              c.withPeers(primaryNode);
              c.withInteropValidators(0, 0);
              applyMilestoneConfig(c);
            });

    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
  }

  private static void applyMilestoneConfig(final Config c) {
    c.withAltairEpoch(UInt64.ZERO);
    c.withBellatrixEpoch(UInt64.ZERO);
    c.withCapellaEpoch(UInt64.ONE);
    c.withStubExecutionEngine();
  }
}
