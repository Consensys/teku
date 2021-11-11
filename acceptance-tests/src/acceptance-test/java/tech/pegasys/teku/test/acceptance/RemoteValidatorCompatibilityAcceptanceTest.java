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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.DockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorCompatibilityAcceptanceTest extends AcceptanceTestBase {
  static final int VALIDATOR_COUNT = 8;

  private TekuNode beaconNode;
  private TekuValidatorNode validatorClient;

  @Test
  void shouldRunUpdatedValidatorAgainstOldBeaconNode() throws Exception {
    verifyCompatibility(DockerVersion.V21_10_0, DockerVersion.LOCAL_BUILD);
  }

  @Test
  @Disabled("Last release currently has incorrect REST API format")
  void shouldRunUpdatedValidatorAgainstLastReleaseBeaconNode() throws Exception {
    verifyCompatibility(DockerVersion.LAST_RELEASE, DockerVersion.LOCAL_BUILD);
  }

  @Test
  @Disabled("Older validator doesn't ignore unknown config items")
  void shouldRunLastReleaseValidatorAgainstUpdatedBeaconNode() throws Exception {
    verifyCompatibility(DockerVersion.LOCAL_BUILD, DockerVersion.V21_10_0);
  }

  private void verifyCompatibility(
      final DockerVersion beaconNodeVersion, final DockerVersion validatorNodeVersion)
      throws Exception {
    createBeaconNode(beaconNodeVersion);
    createValidatorClient(validatorNodeVersion);

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
  }

  private void createValidatorClient(final DockerVersion version) {
    validatorClient =
        createValidatorNode(
            version,
            config ->
                config
                    .withNetwork("auto")
                    .withInteropValidators(0, VALIDATOR_COUNT)
                    .withBeaconNode(beaconNode));
  }

  private void createBeaconNode(final DockerVersion version) {
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
