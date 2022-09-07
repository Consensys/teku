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
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class PostMergeRemoteValidatorAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "less-swift";
  private static final int VALIDATOR_COUNT = 8;
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  private BesuNode executionNode1;
  private TekuNode beaconNode;
  private TekuNode failoverBeaconNode;

  private TekuValidatorNode validatorClient;

  @BeforeEach
  public void setup() throws Exception {
    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    executionNode1 = createBesuNode(this::configurePostMergeBesuNode);
    executionNode1.start();
    final BesuNode executionNode2 = createBesuNode(this::configurePostMergeBesuNode);
    executionNode2.start();

    beaconNode =
        createTekuNode(config -> configurePostMergeTekuNode(config, executionNode1, genesisTime));
    failoverBeaconNode =
        createTekuNode(config -> configurePostMergeTekuNode(config, executionNode2, genesisTime));

    validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withBeaconNodeEventStreamSyncingStatusQueryPeriod(Duration.ofMillis(100))
                    .withBeaconNodes(beaconNode, failoverBeaconNode));
  }

  @Test
  public void shouldFailoverWhenTheExecutionLayerConnectedToThePrimaryBeaconNodeGoesDown()
      throws Exception {

    beaconNode.start();
    failoverBeaconNode.start();
    validatorClient.start();

    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();

    // stop the EL connected to the primary Beacon Node
    executionNode1.stop();

    validatorClient.waitForLogMessageContaining(
        "Switching to failover beacon node for event streaming");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
  }

  private BesuNode.Config configurePostMergeBesuNode(final BesuNode.Config config) {
    return config
        .withMiningEnabled(true)
        .withMergeSupport(true)
        .withGenesisFile("besu/preMergeGenesis.json")
        .withJwtTokenAuthorization(JWT_FILE);
  }

  private TekuNode.Config configurePostMergeTekuNode(
      final TekuNode.Config config, final BesuNode executionEngine, final int genesisTime) {
    return config
        .withNetwork(NETWORK_NAME)
        .withBellatrixEpoch(UInt64.ZERO)
        .withTotalTerminalDifficulty(UInt64.valueOf(10001).toString())
        .withGenesisTime(genesisTime)
        .withRealNetwork()
        .withStartupTargetPeerCount(0)
        .withExecutionEngineEndpoint(executionEngine.getInternalEngineJsonRpcUrl())
        .withJwtSecretFile(JWT_FILE)
        .withInteropNumberOfValidators(VALIDATOR_COUNT)
        .withInteropValidators(0, 0);
  }

  private void waitForSuccessfulEventStreamConnection() {
    validatorClient.waitForLogMessageContaining(
        "Successfully connected to beacon node event stream");
  }

  private void waitForValidatorDutiesToComplete() {
    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
    validatorClient.waitForLogMessageContaining("Published sync_signature");
    validatorClient.waitForLogMessageContaining("Published sync_contribution");
  }
}
