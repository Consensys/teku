/*
 * Copyright 2021 ConsenSys AG.
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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorKeysAcceptanceTest extends AcceptanceTestBase {
  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  void shouldMaintainValidatorsInMutableClient(@TempDir final Path tempDir) throws Exception {
    final String networkName = "less-swift";
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 8);
    final ValidatorKeystores extraKeys =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 1);

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork(networkName).withDepositsFrom(eth1Node));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
                config ->
                    config
                        .withNetwork(networkName)
                        .withValidatorApiEnabled()
                        .withInteropModeDisabled()
                        .withBeaconNode(beaconNode))
            .withMutableValidatorKeystores(validatorKeystores);

    beaconNode.start();
    validatorClient.start();

    getValidatorListing(validatorClient, 0);

    addValidatorsAndExpect(validatorClient, validatorKeystores, tempDir, "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");
    getValidatorListing(validatorClient, 8);

    // second add attempt would be duplicates
    addValidatorsAndExpect(validatorClient, validatorKeystores, tempDir, "duplicate");

    // a random key won't be found, remove should give not_found
    removeValidatorAndCheckStatus(validatorClient, extraKeys.getPublicKeys().get(0), "not_found");

    // wait for epoch 1 to ensure the validator has attested, which will give slashing protection
    // data
    beaconNode.waitForLogMessageContaining("Epoch Event *** Epoch: 2");
    // remove a validator
    final BLSPublicKey removedPubkey = validatorKeystores.getPublicKeys().get(0);
    removeValidatorAndCheckStatus(validatorClient, removedPubkey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed validator");
    validatorClient.waitForLogMessageContaining("Published block");
    getValidatorListing(validatorClient, 7);

    // remove the same validator again
    removeValidatorAndCheckStatus(validatorClient, removedPubkey, "not_active");

    validatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }

  private void addValidatorsAndExpect(
      final TekuValidatorNode validatorClient,
      final ValidatorKeystores validatorKeystores,
      final Path tempDir,
      final String expectedStatus)
      throws IOException {
    final JsonNode addResult =
        jsonProvider
            .getObjectMapper()
            .readTree(validatorClient.addValidators(validatorKeystores, tempDir));
    assertThat(addResult.get("data").size()).isEqualTo(validatorKeystores.getValidatorCount());
    checkStatus(addResult.get("data"), expectedStatus);
  }

  private void removeValidatorAndCheckStatus(
      final TekuValidatorNode validatorClient,
      final BLSPublicKey publicKey,
      final String expectedStatus)
      throws IOException {
    final JsonNode removeResult =
        jsonProvider.getObjectMapper().readTree(validatorClient.removeValidator(publicKey));
    assertThat(removeResult.get("data").size()).isEqualTo(1);
    checkStatus(removeResult.get("data"), expectedStatus);
    if (expectedStatus.equals("deleted") || expectedStatus.equals("not_active")) {
      final JsonNode slashingProtection =
          jsonProvider.getObjectMapper().readTree(removeResult.get("slashing_protection").asText());
      final JsonNode slashingData = slashingProtection.get("data");
      assertThat(slashingData.size()).isEqualTo(1);
      assertThat(slashingData.get(0).get("pubkey").asText()).isEqualTo(publicKey.toString());
    }
  }

  private void checkStatus(final JsonNode data, final String status) {
    assertThat(data.isArray()).isTrue();
    for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
      final JsonNode node = it.next();
      assertThat(node.get("status").asText()).isEqualTo(status);
    }
  }

  private void checkReadOnly(final JsonNode data, final boolean readOnlyStatus) {
    assertThat(data.isArray()).isTrue();
    for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
      final JsonNode node = it.next();
      assertThat(node.get("readonly").asBoolean()).isEqualTo(readOnlyStatus);
    }
  }

  private void getValidatorListing(final TekuValidatorNode validatorClient, final int expectedKeys)
      throws IOException {
    final JsonNode result =
        jsonProvider.getObjectMapper().readTree(validatorClient.getValidatorListing());
    final JsonNode data = result.get("data");
    assertThat(data.isArray()).isTrue();
    assertThat(data.size()).isEqualTo(expectedKeys);
    checkReadOnly(data, false);
  }
}
