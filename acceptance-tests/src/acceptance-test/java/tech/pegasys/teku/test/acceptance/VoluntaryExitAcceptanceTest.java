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

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuVoluntaryExit;

public class VoluntaryExitAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldChangeValidatorStatusAfterSubmittingVoluntaryExit() throws Exception {
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final List<BLSKeyPair> validatorKeys = createKeysAndSendDeposits(eth1Node, 4);

    final File validatorInfoTar = createValidatorKeystores(validatorKeys, "keys", "passwords");

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork("less-swift").withDepositsFrom(eth1Node));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("less-swift")
                    .withInteropModeDisabled()
                    .withValidatorKeys("/opt/teku/keys:/opt/teku/passwords")
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl()));
    validatorClient.copyContentsToWorkingDirectory(validatorInfoTar);

    final TekuVoluntaryExit voluntaryExitProcess =
        createVoluntaryExit(
            config ->
                config
                    .withValidatorKeys("/opt/teku/keys:/opt/teku/passwords")
                    .withBeaconNodeEndpoint(beaconNode.getBeaconRestApiUrl()));
    voluntaryExitProcess.copyContentsToWorkingDirectory(validatorInfoTar);

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");

    voluntaryExitProcess.start();

    validatorClient.waitForLogMessageContaining("has changed status from");
  }
}
