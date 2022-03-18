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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

import static tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion.DEVELOP;

public class SingleNodeBellatrixAcceptanceTest extends AcceptanceTestBase {
    private final String NETWORK_NAME = "less-swift";

    private final SystemTimeProvider timeProvider = new SystemTimeProvider();
    private BesuNode eth1Node;
    private TekuNode tekuNode;

    @BeforeEach
    void setup() throws Exception {
        final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
        eth1Node = createBesuNode(DEVELOP, this::configureBesuNode);
        eth1Node.start();

        final int TOTAL_VALIDATORS = 4;
        final ValidatorKeystores validatorKeystores = createTekuDepositSender(NETWORK_NAME)
                .sendValidatorDeposits(eth1Node, TOTAL_VALIDATORS);

        tekuNode = createTekuNode(config ->
                configureTekuNode(config, genesisTime)
                        .withDepositsFrom(eth1Node)
                        .withValidatorKeystores(validatorKeystores)
                        .withValidatorProposerDefaultFeeRecipient("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")
                        .withExecutionEngineEndpoint(eth1Node.getInternalEngineJsonRpcUrl()));
        tekuNode.start();
    }

    @Test
    void shouldHaveNonDefaultExecutionPayloadAndFinalizeAfterMergeTransition() {
        tekuNode.waitForGenesis();
        tekuNode.waitForNonDefaultExecutionPayload();
        tekuNode.waitForNewFinalization();
    }

    private TekuNode.Config configureTekuNode(final TekuNode.Config node, final int genesisTime) {
        return node.withNetwork(NETWORK_NAME)
                .withBellatrixEpoch(UInt64.ONE)
                .withTotalTerminalDifficulty(UInt64.valueOf(10001).toString())
                .withGenesisTime(genesisTime);
    }

    private BesuNode.Config configureBesuNode(BesuNode.Config config) {
        return config.withRpcHttpApi("ETH,NET,WEB3,ENGINE")
            .withDefaultEngineRpcHttpPort()
            .withEngineHostAllowList("*")
            .withMergeSupport(true)
            .withGenesisFile("besu/preMergeGenesis.json");
    }
}
