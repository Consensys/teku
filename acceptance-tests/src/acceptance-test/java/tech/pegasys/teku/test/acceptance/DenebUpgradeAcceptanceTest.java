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

import com.google.common.io.Resources;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DenebUpgradeAcceptanceTest extends AcceptanceTestBase {
  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldUpgradeToDeneb() throws Exception {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final int genesisTime = currentTime.plus(60).intValue(); // magic node startup time
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

    TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            beaconNodeWithTrustedSetup(genesisTime, primaryEL)
                .withStartupTargetPeerCount(0)
                .build());

    primaryNode.start();
    primaryNode.waitForMilestone(SpecMilestone.DENEB);

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

    final int primaryNodeGenesisTime = primaryNode.getGenesisTime().intValue();

    TekuBeaconNode lateJoiningNode =
        createTekuBeaconNode(
            beaconNodeWithTrustedSetup(primaryNodeGenesisTime, secondaryEL)
                .withPeers(primaryNode)
                .withInteropValidators(0, 0)
                .build());

    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    primaryNode.waitForNewBlock();
  }

  private static TekuNodeConfigBuilder beaconNodeWithTrustedSetup(
      final int genesisTime, final BesuNode besuNode) throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(UInt64.ONE)
        .withGenesisTime(genesisTime)
        .withExecutionEngine(besuNode)
        .withRealNetwork()
        .withJwtSecretFile(JWT_FILE)
        .withDenebEpoch(UInt64.valueOf(2))
        .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0);
  }
}
