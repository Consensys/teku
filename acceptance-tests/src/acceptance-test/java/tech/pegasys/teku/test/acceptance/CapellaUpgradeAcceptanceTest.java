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
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class CapellaUpgradeAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");
  public static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  @Test
  void shouldUpgradeToCapella() throws Exception {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final int genesisTime = currentTime.plus(15).intValue();
    final int shanghaiTime = genesisTime + 4 * 2; // 4 slots, 2 seconds each (swift)

    BesuNode primaryEL =
        createBesuNode(
            BesuDockerVersion.STABLE,
            c -> {
              c.withMergeSupport(true);
              c.withGenesisFile("besu/shanghaiGenesis.json");
              //              c.withP2pEnabled(true);
              c.withJwtTokenAuthorization(JWT_FILE);
            },
            Map.of("shanghaiTime", String.valueOf(shanghaiTime)));
    primaryEL.start();

    //    BesuNode secondaryEL =
    //        createBesuNode(
    //            c -> {
    //              c.withMergeSupport(true);
    //              c.withGenesisFile("besu/shanghaiGenesis.json");
    //              c.withP2pEnabled(true);
    //              c.withJwtTokenAuthorization(JWT_FILE);
    //            });
    //    secondaryEL.start();
    //    secondaryEL.addPeer(primaryEL);

    final ValidatorKeystores validatorKeys =
        createTekuDepositSender(NETWORK_NAME).generateValidatorKeys(4, WITHDRAWAL_ADDRESS);
    final InitialStateData initialStateData =
        createGenesisGenerator()
            .network(NETWORK_NAME)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withCapellaEpoch(UInt64.ONE)
            .withTotalTerminalDifficulty(0)
            .genesisPayloadSource(primaryEL)
            .validatorKeys(validatorKeys)
            .genesisTime(genesisTime)
            .generate();

    TekuNode primaryNode =
        createTekuNode(
            c -> {
              c.withRealNetwork();
              c.withStartupTargetPeerCount(0);
              c.withAltairEpoch(UInt64.ZERO);
              c.withBellatrixEpoch(UInt64.ZERO);
              c.withCapellaEpoch(UInt64.ONE);
//              c.withGenesisTime(genesisTime);
              c.withExecutionEngine(primaryEL);
              c.withJwtSecretFile(JWT_FILE);
              c.withInitialState(initialStateData);
              c.withValidatorKeystores(validatorKeys);
              c.withValidatorProposerDefaultFeeRecipient(
                  "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");
              c.withEngineApiMethodNegotiation();
//              applyMilestoneConfig(c);
            });
    primaryNode.start();
    primaryNode.waitForMilestone(SpecMilestone.CAPELLA);

    //    UInt64 genesisTime = primaryNode.getGenesisTime();

    //    TekuNode lateJoiningNode =
    //        createTekuNode(
    //            c -> {
    //              c.withGenesisTime(genesisTime.intValue());
    //              c.withRealNetwork();
    //              c.withPeers(primaryNode);
    //              c.withInteropValidators(0, 0);
    //              c.withExecutionEngine(primaryEL);
    //              c.withJwtSecretFile(JWT_FILE);
    //              c.withEngineApiMethodNegotiation();
    //              c.withTotalTerminalDifficulty(0);
    //              c.withInitialState(initialStateData);
    //              applyMilestoneConfig(c);
    //            });

    //    lateJoiningNode.start();
    //    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    primaryNode.waitForNewBlock();
  }

//  private static void applyMilestoneConfig(final Config c) {
//    c.withAltairEpoch(UInt64.ZERO);
//    c.withBellatrixEpoch(UInt64.ZERO);
//    c.withCapellaEpoch(UInt64.valueOf(2));
//  }
}
