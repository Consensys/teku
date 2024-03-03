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

import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorClientServiceAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnBeaconNode() throws Exception {
    final TekuNodeConfig tekuNodeConfig =
        TekuNodeConfigBuilder.createBeaconNode()
            .withInteropValidators(0, 0)
            .withExitWhenNoValidatorKeysEnabled(true)
            .build();
    final TekuBeaconNode beaconNode = createTekuBeaconNode(tekuNodeConfig);
    beaconNode.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
  }

  @Test
  void bn_shouldFailIfValidatorKeyLocked(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores initialKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withWritableKeystorePath(initialKeystores, tempDir)
                .withInitialState(genesis)
                .build());

    beaconNode.startWithFailure(
        "Unable to initialize validator keys, please manually correct errors and try again.");
  }

  @Test
  void vc_shouldFailIfValidatorKeyLocked(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores initialKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withInitialState(genesis).build());
    beaconNode.start();

    final TekuValidatorNode validatorNode =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withBeaconNodes(beaconNode)
                .withNetwork("auto")
                .withWritableKeystorePath(initialKeystores, tempDir)
                .build());

    validatorNode.startWithFailure(
        "Unable to initialize validator keys, please manually correct errors and try again.");
  }

  @Test
  void bn_shouldFailIfCannotLockKeys(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    // keys aren't locked, but we're on readonly filesystem, so will cause unchecked io exception
    final ValidatorKeystores initialKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2, false);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(initialKeystores, tempDir)
                .build());

    beaconNode.startWithFailure(
        "Unable to initialize validator keys, please manually correct errors and try again.");
  }

  @Test
  void bn_shouldFailIfCannotLockKeysUnhandledException() throws Exception {
    final String networkName = "swift";
    // keys aren't locked, but we have no access to write
    final ValidatorKeystores initialKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(initialKeystores)
                .withValidatorKeystoreLockingEnabled(true)
                .build());

    beaconNode.startWithFailure(
        "Unable to initialize validator keys, please manually correct errors and try again.");
  }

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnValidatorClient() throws Exception {
    final TekuBeaconNode beaconNode = createTekuBeaconNode();

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withExitWhenNoValidatorKeysEnabled(true)
                .build());
    beaconNode.start();
    validatorClient.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnBeaconNode() throws Exception {
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withExitWhenNoValidatorKeysEnabled(false)
                .withInteropValidators(0, 0)
                .build());
    beaconNode.start();

    beaconNode.waitForEpochAtOrAbove(1);

    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnValidatorClient() throws Exception {
    final TekuBeaconNode beaconNode = createTekuBeaconNode();
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withExitWhenNoValidatorKeysEnabled(false)
                .build());
    beaconNode.start();
    validatorClient.start();
    beaconNode.waitForEpochAtOrAbove(1);

    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();
    api.assertLocalValidatorListing(Collections.emptyList());
    validatorClient.stop();
    beaconNode.stop();
  }
}
