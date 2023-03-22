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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;

public class CapellaUpgradeAcceptanceTest extends AcceptanceTestBase {

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldUpgradeToCapella() throws Exception {
    BesuNode primaryEL =
        createBesuNode(
            c -> {
              c.withMergeSupport(true);
              c.withGenesisFile("besu/mergedGenesis.json");
              c.withP2pEnabled(true);
              c.withJwtTokenAuthorization(JWT_FILE);
            });
    primaryEL.start();

    BesuNode secondaryEL =
        createBesuNode(
            c -> {
              c.withMergeSupport(true);
              c.withGenesisFile("besu/mergedGenesis.json");
              c.withP2pEnabled(true);
              c.withJwtTokenAuthorization(JWT_FILE);
            });
    secondaryEL.start();
    secondaryEL.addPeer(primaryEL);

    TekuNode primaryNode =
        createTekuNode(
            c -> {
              c.withRealNetwork().withStartupTargetPeerCount(0);
              c.withExecutionEngine(primaryEL);
              c.withJwtSecretFile(JWT_FILE);
              c.withEngineApiMethodNegotiation();
              applyMilestoneConfig(c);
            });

    primaryNode.start();
    primaryNode.waitForMilestone(SpecMilestone.CAPELLA);

    UInt64 genesisTime = primaryNode.getGenesisTime();

    TekuNode lateJoiningNode =
        createTekuNode(
            c -> {
              c.withGenesisTime(genesisTime.intValue());
              c.withRealNetwork();
              c.withPeers(primaryNode);
              c.withInteropValidators(0, 0);
              c.withExecutionEngine(primaryEL);
              c.withJwtSecretFile(JWT_FILE);
              c.withEngineApiMethodNegotiation();
              applyMilestoneConfig(c);
            });

    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    primaryNode.waitForNewBlock();
  }

  private static void applyMilestoneConfig(final Config c) {
    c.withAltairEpoch(UInt64.ZERO);
    c.withBellatrixEpoch(UInt64.ZERO);
    c.withCapellaEpoch(UInt64.ONE);
  }
}
