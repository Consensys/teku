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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorAcceptanceTest extends AcceptanceTestBase {
  static final int VALIDATOR_COUNT = 8;

  @Test
  void shouldCreateAttestationsWithRemoteValidator() throws Exception {
    final TekuNode beaconNode = getBeaconNode();
    final TekuValidatorNode validatorClient = getValidatorClient(beaconNode.getBeaconRestApiUrl());

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidatorStartingFirst() throws Exception {

    final TekuNode beaconNode = getBeaconNode();
    final TekuValidatorNode validatorClient = getValidatorClient(beaconNode.getBeaconRestApiUrl());

    validatorClient.start();
    validatorClient.waitForLogMessageContaining(
        "Error while connecting to beacon node event stream");

    beaconNode.start();

    validatorClient.waitForLogMessageContaining("Connected to EventSource stream");
    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
  }

  private TekuNode getBeaconNode() {
    return createTekuNode(
        config ->
            config
                .withNetwork("minimal")
                .withInteropNumberOfValidators(VALIDATOR_COUNT)
                .withInteropValidators(0, 0)
                .withRestHostsAllowed("*"));
  }

  private TekuValidatorNode getValidatorClient(final String beaconRestUrl) {
    return createValidatorNode(
        config ->
            config
                .withNetwork("minimal")
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodeEndpoint(beaconRestUrl));
  }
}
