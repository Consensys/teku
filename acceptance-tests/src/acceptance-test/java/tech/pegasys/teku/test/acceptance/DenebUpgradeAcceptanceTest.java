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
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;

/**
 * The test is based on `shanghaiTime` and `cancunTime` in Besu EL genesis config as the only option
 * to start these forks on Besu. There is a bit of magic on assumption of node starting time, which
 * should be eliminated when there will be an options `shanghaiBlock` and `cancunBlock`. When it's
 * available, please, upgrade the test to use them instead of time and enable it.
 */
public class DenebUpgradeAcceptanceTest extends AcceptanceTestBase {
  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  @Disabled("Switch Besu genesis to shanghaiBlock and cancunBlock when it's available and enable")
  void shouldUpgradeToDeneb() throws Exception {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final int genesisTime = currentTime.plus(30).intValue(); // magic node startup time
    final int epochDuration = 4 * 2; // 4 slots, 2 seconds each for swift
    final int shanghaiTime = genesisTime + epochDuration;
    final Map<String, String> genesisOverrides =
        Map.of(
            "shanghaiTime",
            String.valueOf(shanghaiTime),
            "cancunTime",
            String.valueOf(shanghaiTime + epochDuration));

    BesuNode primaryEL =
        createBesuNode(
            BesuDockerVersion.STABLE,
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/mergedGenesis.json")
                    .withP2pEnabled(true)
                    .withJwtTokenAuthorization(JWT_FILE),
            genesisOverrides);
    primaryEL.start();

    BesuNode secondaryEL =
        createBesuNode(
            BesuDockerVersion.STABLE,
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/mergedGenesis.json")
                    .withP2pEnabled(true)
                    .withJwtTokenAuthorization(JWT_FILE),
            genesisOverrides);
    secondaryEL.start();
    secondaryEL.addPeer(primaryEL);

    TekuNode primaryNode =
        createTekuNode(
            config -> {
              config
                  .withGenesisTime(genesisTime)
                  .withRealNetwork()
                  .withStartupTargetPeerCount(0)
                  .withExecutionEngine(primaryEL)
                  .withJwtSecretFile(JWT_FILE);
              applyMilestoneConfig(config);
            });

    primaryNode.start();
    primaryNode.waitForMilestone(SpecMilestone.DENEB);

    final int primaryNodeGenesisTime = primaryNode.getGenesisTime().intValue();

    TekuNode lateJoiningNode =
        createTekuNode(
            c -> {
              c.withGenesisTime(primaryNodeGenesisTime)
                  .withRealNetwork()
                  .withPeers(primaryNode)
                  .withInteropValidators(0, 0)
                  .withExecutionEngine(secondaryEL)
                  .withJwtSecretFile(JWT_FILE);
              applyMilestoneConfig(c);
            });

    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    primaryNode.waitForNewBlock();
  }

  private static void applyMilestoneConfig(final Config config) {
    config
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(UInt64.ONE)
        .withDenebEpoch(UInt64.valueOf(2))
        .withTrustedSetupFromClasspath("mainnet-trusted-setup.txt")
        .withTotalTerminalDifficulty(0);
  }
}
