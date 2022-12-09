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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuVoluntaryExit;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class VoluntaryExitAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldChangeValidatorStatusAfterSubmittingVoluntaryExit() throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(4);
    final ValidatorKeystores extraKeys =
        createTekuDepositSender(networkName).generateValidatorKeys(1);

    final InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(validatorKeystores).generate();

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork(networkName).withInitialState(genesis));

    final TekuVoluntaryExit voluntaryExitProcessFailing =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeystores);

    final TekuVoluntaryExit voluntaryExitProcessSuccessful =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeystores)
            .withValidatorKeystores(extraKeys);

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

    beaconNode.waitForEpochAtOrAbove(1);
    voluntaryExitProcessFailing.start();

    beaconNode.waitForEpochAtOrAbove(3);
    voluntaryExitProcessSuccessful.start();
    validatorClient.waitForLogMessageContaining("has changed status from");
    voluntaryExitProcessFailing.waitForExit();
    voluntaryExitProcessSuccessful.waitForExit();
    final List<Integer> validatorIds =
        Arrays.stream(voluntaryExitProcessFailing.getLoggedErrors().split(System.lineSeparator()))
            .filter(s -> s.contains("Validator cannot exit until epoch 3"))
            .map(s -> Integer.parseInt(s.substring(19, 20)))
            .collect(Collectors.toList());
    assertThat(validatorIds.size()).isEqualTo(4);
    assertThat(validatorIds).containsExactlyInAnyOrder(0, 1, 2, 3);
    assertThat(voluntaryExitProcessSuccessful.getLoggedErrors())
        .contains("Validator not found: " + extraKeys.getPublicKeys().get(0).toString());
  }
}
