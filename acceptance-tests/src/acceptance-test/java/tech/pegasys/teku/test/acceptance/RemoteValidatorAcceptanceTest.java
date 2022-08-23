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

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorAcceptanceTest extends AcceptanceTestBase {

  static final int VALIDATOR_COUNT = 8;

  private TekuNode beaconNode;
  private TekuValidatorNode validatorClient;

  @BeforeEach
  public void setup() {
    beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork("swift")
                    .withInteropNumberOfValidators(VALIDATOR_COUNT)
                    .withInteropValidators(0, 0));
    validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("swift")
                    .withInteropValidators(0, VALIDATOR_COUNT)
                    .withBeaconNode(beaconNode));
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidator() throws Exception {
    beaconNode.start();
    validatorClient.start();

    waitForValidatorDutiesToComplete();
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidatorStartingFirst() throws Exception {
    validatorClient.start();
    validatorClient.waitForLogMessageContaining(
        "Error while connecting to beacon node event stream");

    beaconNode.start();

    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
  }

  @Test
  void shouldFailoverWhenPrimaryBeaconNodeGoesDown() throws Exception {
    final TekuNode failoverBeaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork("swift")
                    .withInteropNumberOfValidators(VALIDATOR_COUNT)
                    .withInteropValidators(0, 0)
                    .withPeers(beaconNode));

    beaconNode.start();
    failoverBeaconNode.start();

    validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("swift")
                    .withInteropValidators(0, VALIDATOR_COUNT)
                    .withPrimaryBeaconNodeEventStreamReconnectAttemptPeriod(Duration.ofMillis(100))
                    .withBeaconNodeApiEndpoint(beaconNode, failoverBeaconNode));

    validatorClient.start();

    // validator using `--beacon-node-api-endpoints` instead of `--beacon-node-api-endpoint`
    TekuValidatorNode validatorClient2 = createValidatorNode(
            config ->
                    config
                            .withNetwork("swift")
                            .withInteropValidators(0, VALIDATOR_COUNT)
                            .withPrimaryBeaconNodeEventStreamReconnectAttemptPeriod(Duration.ofMillis(100))
                            .withBeaconNodeApiEndpoints(beaconNode, failoverBeaconNode));

    validatorClient2.start();

    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();

    beaconNode.stop();

    validatorClient.waitForLogMessageContaining(
        "Switching to failover beacon node for event streaming");
    validatorClient2.waitForLogMessageContaining(
            "Switching to failover beacon node for event streaming");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();

    // primary beacon node recovers
    beaconNode.start();

    validatorClient.waitForLogMessageContaining(
        "Primary beacon node is back and ready for event streaming. Will attempt connecting.");
    validatorClient2.waitForLogMessageContaining(
            "Primary beacon node is back and ready for event streaming. Will attempt connecting.");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
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
