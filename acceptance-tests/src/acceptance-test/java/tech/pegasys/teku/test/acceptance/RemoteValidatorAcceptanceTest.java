/*
 * Copyright 2020 ConsenSys AG.
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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorAcceptanceTest extends AcceptanceTestBase {
  @Test
  void shouldCreateAttestationsWithRemoteValidator() throws Exception {
    final int validatorCount = 8;
    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropNumberOfValidators(validatorCount)
                    .withInteropValidators(0, 0)
                    .withRestHostsAllowed("*"));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropValidators(0, validatorCount)
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl()));

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");

    assertThat(validatorClient.getLoggedErrors()).isEmpty();
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidatorStartingFirst() throws Exception {
    final int validatorCount = 8;
    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropNumberOfValidators(validatorCount)
                    .withInteropValidators(0, 0)
                    .withRestHostsAllowed("*"));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropValidators(0, validatorCount)
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl()));

    validatorClient.start();
    validatorClient.waitForLogMessageContaining(
        "Error while connecting to beacon node event stream");

    beaconNode.start();

    validatorClient.waitForLogMessageContaining("Connected to EventSource stream");
    validatorClient.waitForLogMessageContaining("Published block");

    // straight after this message we start to see attestations and aggregations
    beaconNode.waitForLogMessageContaining("Updating number of persistent subnet subscriptions");

    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
  }
}
