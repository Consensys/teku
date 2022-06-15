/*
 * Copyright 2021 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class OptimisticSafeSlotsSyncPostMergeAcceptanceTest extends AcceptanceTestBase {
  private static final String NETWORK_NAME = "less-swift";
  private static final Integer SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY = 4;

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private BesuNode eth1Node1;
  private BesuNode eth1Node2;
  private TekuNode tekuNode1;
  private TekuNode tekuNode2;

  @BeforeEach
  void setup() throws Exception {
    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    eth1Node1 =
        createBesuNode(
            config ->
                config
                    .withMiningEnabled(true)
                    .withMergeSupport(true)
                    .withP2pEnabled()
                    .withGenesisFile("besu/preMergeGenesis.json"));
    eth1Node1.start();
    eth1Node2 =
        createBesuNode(
            config ->
                config
                    .withMergeSupport(true)
                    .withP2pEnabled()
                    .withGenesisFile("besu/preMergeGenesis.json")
                    .withStaticPeers(eth1Node1));
    eth1Node2.start();

    final int totalValidators = 4;
    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(NETWORK_NAME).sendValidatorDeposits(eth1Node1, totalValidators);
    tekuNode1 =
        createTekuNode(
            config ->
                configureTekuNode(config, eth1Node1, genesisTime)
                    .withValidatorKeystores(validatorKeystores)
                    .withValidatorProposerDefaultFeeRecipient(
                        "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));
    tekuNode1.start();
    tekuNode2 =
        createTekuNode(
            config ->
                configureTekuNode(config, eth1Node2, genesisTime)
                    .withInteropValidators(0, 0)
                    .withPeers(tekuNode1)
                    .withSafeSlotsToImportOptimistically(SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY));
    tekuNode2.start();
  }

  @Test
  void shouldPassMergeOptimisticallyAndBeginFinalizationAfterSafeSlotsToImport() throws Exception {
    tekuNode2.waitForGenesis();
    // Disconnect eth1Node2 from eth1Node1 before the merge
    assertThat(eth1Node2.removePeer(eth1Node1)).isTrue();
    tekuNode1.waitForLogMessageContaining("MERGE is completed");

    tekuNode2.waitForLogMessageContaining("optimistic sync");
    tekuNode2.waitForLogMessageContaining("MERGE is completed");
    tekuNode2.waitForNonDefaultExecutionPayload();
    tekuNode2.waitForNewFinalization();
    tekuNode2.waitForOptimisticBlock();
  }

  private TekuNode.Config configureTekuNode(
      final TekuNode.Config config, final BesuNode executionEngine, final int genesisTime) {
    return config
        .withNetwork(NETWORK_NAME)
        .withBellatrixEpoch(UInt64.ONE)
        .withTotalTerminalDifficulty(UInt64.valueOf(10001).toString())
        .withGenesisTime(genesisTime)
        .withRealNetwork()
        .withDepositsFrom(executionEngine)
        .withStartupTargetPeerCount(0)
        .withExecutionEngineEndpoint(executionEngine.getInternalEngineJsonRpcUrl())
        .withJwtSecretFile(Resources.getResource("teku/ee-jwt-secret.hex"));
  }
}
