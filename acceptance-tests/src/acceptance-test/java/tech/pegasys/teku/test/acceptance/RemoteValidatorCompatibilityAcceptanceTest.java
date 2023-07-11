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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorCompatibilityAcceptanceTest extends AcceptanceTestBase {
  static final int VALIDATOR_COUNT = 8;

  private TekuNode beaconNode;
  private TekuValidatorNode validatorClient;

  @Test
  void shouldRunUpdatedValidatorAgainstOldBeaconNode() throws Exception {
    verifyCompatibility(TekuDockerVersion.V22_8_1, TekuDockerVersion.LOCAL_BUILD);
  }

  @Test
  void shouldRunUpdatedValidatorAgainstLastReleaseBeaconNode() throws Exception {
    verifyCompatibility(TekuDockerVersion.LAST_RELEASE, TekuDockerVersion.LOCAL_BUILD);
  }

  @Test
  void shouldRunLastReleaseValidatorAgainstUpdatedBeaconNode() throws Exception {
    verifyCompatibility(TekuDockerVersion.LOCAL_BUILD, TekuDockerVersion.LAST_RELEASE);
  }

  private void verifyCompatibility(
      final TekuDockerVersion beaconNodeVersion, final TekuDockerVersion validatorNodeVersion)
      throws Exception {
    createBeaconNode(beaconNodeVersion);
    createValidatorClient(validatorNodeVersion);

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
  }

  private void createValidatorClient(final TekuDockerVersion version) {
    validatorClient =
        createValidatorNode(
            version,
            config ->
                config
                    .withNetwork("auto")
                    .withInteropValidators(0, VALIDATOR_COUNT)
                    .withBeaconNode(beaconNode));
  }

  private void createBeaconNode(final TekuDockerVersion version) {
    beaconNode =
        createTekuNode(
            version,
            config ->
                config
                    .withNetwork("swift")
                    .withInteropNumberOfValidators(VALIDATOR_COUNT)
                    .withInteropValidators(0, 0));
  }
}
