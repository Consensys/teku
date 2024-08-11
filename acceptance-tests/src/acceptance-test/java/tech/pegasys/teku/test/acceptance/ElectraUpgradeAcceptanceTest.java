/*
 * Copyright Consensys Software Inc., 2022
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
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class ElectraUpgradeAcceptanceTest extends AcceptanceTestBase {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void foo() throws Exception {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final int genesisTime = currentTime.plus(1).intValue(); // magic node startup time

    BesuNode primaryEL =
        createBesuNode(
            BesuDockerVersion.STABLE,
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/pragueGenesis.json")
                    .withP2pEnabled(true)
                    .withJwtTokenAuthorization(JWT_FILE));
    primaryEL.start();

    TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            beaconNodeConfigWithForks(genesisTime, primaryEL)
                .withStartupTargetPeerCount(0)
                .build());

    primaryNode.start();
//    primaryNode.waitForMilestone(SpecMilestone.CAPELLA);

    primaryEL.waitForExit(1_000_000);
  }

  private static TekuNodeConfigBuilder beaconNodeConfigWithForks(
      final int genesisTime, final BesuNode besuNode) throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork("minimal")
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(UInt64.ZERO)
        .withDenebEpoch(UInt64.ZERO)
        .withElectraEpoch(UInt64.ZERO)
        .withTotalTerminalDifficulty(0)
        .withGenesisTime(genesisTime)
        .withExecutionEngine(besuNode)
        .withJwtSecretFile(JWT_FILE)
        .withRealNetwork();
  }
}
