/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class DoppelgangerDetectorAcceptanceTest extends AcceptanceTestBase {

  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  private final String networkName = "less-swift";

  private BesuNode eth1Node;

  @BeforeEach
  void setUp() {
    eth1Node =
        createBesuNode(
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/preMergeGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
  }

  @Test
  void shouldDetectDoppelgangersViaKeyManagerApiForKeystoresWithValidPassword() throws Exception {
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(2);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(validatorKeystores).generate();

    final String defaultFeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            configureBeaconNode(eth1Node)
                .withValidatorLivenessTracking()
                .withJwtSecretFile(JWT_FILE)
                .withBellatrixEpoch(UInt64.ONE)
                .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInitialState(genesis)
                .build());

    final TekuValidatorNode firstValidatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withNetwork("auto")
                .withDoppelgangerDetectionEnabled()
                .build());
    final ValidatorKeysApi api = firstValidatorClient.getValidatorKeysApi();

    beaconNode.start();
    firstValidatorClient.start();

    api.assertLocalValidatorListing(Collections.emptyList());
    final String incorrectPassword = "incorrectPassword";

    // Only the validator with the valid password is imported
    api.addLocalValidatorsAndExpect(
        validatorKeystores,
        List.of(validatorKeystores.getPasswords().getFirst(), incorrectPassword),
        List.of("imported", "error"),
        Optional.of(
            List.of(
                "",
                "Invalid keystore password for public key: "
                    + validatorKeystores.getPublicKeys().get(1).toAbbreviatedString())));

    // Perform doppelganger check for the valid imported validator only
    firstValidatorClient.waitForLogMessageEndingWith(
        "Starting doppelganger detection for public keys: "
            + validatorKeystores.getPublicKeys().getFirst().toAbbreviatedString());
    firstValidatorClient.waitForLogMessageContaining("No validators doppelganger detected");

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withNetwork("auto")
                .withDoppelgangerDetectionEnabled()
                .build());

    final ValidatorKeysApi secondApi = secondValidatorClient.getValidatorKeysApi();

    secondValidatorClient.start();

    secondApi.assertLocalValidatorListing(Collections.emptyList());

    secondApi.addLocalValidatorsAndExpect(validatorKeystores, "imported");

    secondValidatorClient.waitForLogMessageContaining("Validator doppelganger detected...");
    secondValidatorClient.waitForLogMessageContaining("Doppelganger detection check finished");
    secondValidatorClient.waitForLogMessageContaining("Detected 1 validators doppelganger");
    secondValidatorClient.waitForLogMessageContaining(
        "Detected 1 active validators doppelganger. The following keys have been ignored");

    firstValidatorClient.stop();
    secondValidatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }

  @Test
  void shouldDetectDoppelgangersAtStartUp() throws Exception {

    eth1Node.start();

    final ValidatorKeystores keyStore =
        createTekuDepositSender(networkName).generateValidatorKeys(2);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(keyStore).generate();

    final TekuBeaconNode firstNode =
        createTekuBeaconNode(
            configureBeaconNode(eth1Node)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(keyStore)
                .build());
    firstNode.start();

    firstNode.waitForOwnedValidatorCount(2);
    firstNode.waitForGenesis();

    firstNode.waitForEpochAtOrAbove(2);

    final TekuBeaconNode secondNode =
        createTekuBeaconNode(
            configureBeaconNode(eth1Node)
                .withPeers(firstNode)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(keyStore)
                .withDoppelgangerDetectionEnabled()
                .build());

    secondNode.start();

    secondNode.waitForLogMessageContaining("Validator doppelganger detected...");
    secondNode.waitForLogMessageContaining("Detected 2 validators doppelganger");
    secondNode.waitForLogMessageContaining(
        "Doppelganger detection check finished. Stopping doppelganger detection");
    secondNode.waitForLogMessageContaining("Detected 2 validators doppelganger");
    secondNode.waitForLogMessageContaining("Shutting down...");

    secondNode.stop();
    eth1Node.stop();
  }

  private TekuNodeConfigBuilder configureBeaconNode(final BesuNode eth1Node) throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withDepositsFrom(eth1Node)
        .withRealNetwork()
        .withExecutionEngine(eth1Node)
        .withNetwork(networkName);
  }
}
