/*
 * Copyright Consensys Software Inc., 2023
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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

public class ValidatorClientServiceAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnBeaconNode() throws Exception {
    final TekuNode beaconNode =
        createTekuNode(
            config -> config.withExitWhenNoValidatorKeysEnabled(true).withInteropValidators(0, 0));
    beaconNode.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
  }

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnValidatorClient() throws Exception {
    final TekuNode beaconNode = createTekuNode();
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withBeaconNode(beaconNode)
                    .withExitWhenNoValidatorKeysEnabled(true)
                    .withInteropModeDisabled());
    beaconNode.start();
    validatorClient.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithValidatorKeysUnknownStatusWhenExitOptionEnabledOnBeaconNode()
      throws Exception {
    final TekuNode beaconNode =
        createTekuNode(
            // load interop keys outside genesis range
            config ->
                config
                    .withExitWhenNoValidatorKeysEnabled(true)
                    .withInteropNumberOfValidators(10)
                    .withInteropValidators(20, 2));
    beaconNode.start();

    beaconNode.waitForEpochAtOrAbove(1);

    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithValidatorKeysUnknownStatusWhenExitOptionEnabledOnValidatorClient()
      throws Exception {
    final TekuNode beaconNode = createTekuNode(config -> config.withInteropNumberOfValidators(10));
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withBeaconNode(beaconNode)
                    .withExitWhenNoValidatorKeysEnabled(true)
                    .withValidatorApiEnabled()
                    // load interop keys outside genesis range
                    .withInteropValidators(20, 2));
    beaconNode.start();
    validatorClient.start();
    beaconNode.waitForEpochAtOrAbove(1);

    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();
    api.assertLocalValidatorCount(2);
    validatorClient.stop();
    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnBeaconNode() throws Exception {
    final TekuNode beaconNode =
        createTekuNode(
            config -> config.withExitWhenNoValidatorKeysEnabled(false).withInteropValidators(0, 0));
    beaconNode.start();

    beaconNode.waitForEpochAtOrAbove(1);

    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnValidatorClient() throws Exception {
    final TekuNode beaconNode = createTekuNode();
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withBeaconNode(beaconNode)
                    .withExitWhenNoValidatorKeysEnabled(false)
                    .withValidatorApiEnabled()
                    .withInteropModeDisabled());
    beaconNode.start();
    validatorClient.start();
    beaconNode.waitForEpochAtOrAbove(1);

    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();
    api.assertLocalValidatorListing(Collections.emptyList());
    validatorClient.stop();
    beaconNode.stop();
  }
}
