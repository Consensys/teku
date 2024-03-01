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
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

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
  void bn_shouldFailIfValidatorKeyLocked(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores initialKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withInitialState(genesis)
                    .withWritableKeystorePath(initialKeystores, tempDir));

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

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork(networkName).withInitialState(genesis));
    beaconNode.start();

    final TekuValidatorNode validatorNode =
        createValidatorNode(config -> config.withBeaconNode(beaconNode).withNetwork("auto"))
            .withWritableKeystorePath(initialKeystores, tempDir);

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

    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withInitialState(genesis)
                    .withReadOnlyKeystorePath(initialKeystores, tempDir));

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

    final TekuNode beaconNode =
        createTekuNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withInitialState(genesis)
                    .withReadOnlyKeystorePath(initialKeystores)
                    .withValidatorKeystoreLockingEnabled(true));

    beaconNode.startWithFailure(
        "Unable to initialize validator keys, please manually correct errors and try again.");
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
