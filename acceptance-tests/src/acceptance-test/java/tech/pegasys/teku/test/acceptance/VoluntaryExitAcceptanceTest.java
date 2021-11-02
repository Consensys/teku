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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuVoluntaryExit;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class VoluntaryExitAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldChangeValidatorStatusAfterSubmittingVoluntaryExit() throws Exception {
    final String networkName = "less-swift";
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 4);

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork(networkName).withDepositsFrom(eth1Node));

    final TekuVoluntaryExit voluntaryExitProcessFailing =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeystores);

    final TekuVoluntaryExit voluntaryExitProcessSuccessful =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeystores);

    final TekuValidatorNode validatorClient =
        createValidatorNode(
                config ->
                    config
                        .withNetwork(networkName)
                        .withInteropModeDisabled()
                        .withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeystores);

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");

    beaconNode.waitForLogMessageContaining("Epoch: 1");
    voluntaryExitProcessFailing.start();

    beaconNode.waitForLogMessageContaining("Epoch: 3");
    voluntaryExitProcessSuccessful.start();
    validatorClient.waitForLogMessageContaining("has changed status from");
    assertThat(voluntaryExitProcessFailing.getLoggedErrors())
        .contains("Failed to submit exit for validator");
  }
}
