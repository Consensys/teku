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

package tech.pegasys.teku.test.acceptance.dsl.tools;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.acceptance.dsl.SimpleHttpClient;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorKeysApi {
  private static final Logger LOG = LogManager.getLogger();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final SimpleHttpClient httpClient;
  private final Supplier<URI> validatorUri;

  public ValidatorKeysApi(final SimpleHttpClient httpClient, final Supplier<URI> validatorUri) {
    this.httpClient = httpClient;
    this.validatorUri = validatorUri;
  }

  public void addValidatorsAndExpect(
      final ValidatorKeystores validatorKeystores, final String expectedStatus) throws IOException {
    final Path tempDir = Files.createTempDirectory("validator-keys-api");
    final JsonNode addResult =
        jsonProvider.getObjectMapper().readTree(addValidators(validatorKeystores, tempDir));
    assertThat(addResult.get("data").size()).isEqualTo(validatorKeystores.getValidatorCount());
    checkStatus(addResult.get("data"), expectedStatus);
    tempDir.toFile().delete();
  }

  public void removeValidatorAndCheckStatus(
      final BLSPublicKey publicKey, final String expectedStatus) throws IOException {
    final JsonNode removeResult =
        jsonProvider.getObjectMapper().readTree(removeValidator(publicKey));
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

  public void assertValidatorListing(final List<BLSPublicKey> expectedKeys) throws IOException {
    final JsonNode result = jsonProvider.getObjectMapper().readTree(getValidatorListing());
    final JsonNode data = result.get("data");
    assertThat(data.isArray()).isTrue();
    final List<String> expectedKeyStrings =
        expectedKeys.stream()
            .map(key -> key.toBytesCompressed().toHexString())
            .collect(Collectors.toList());
    final List<String> actualKeyStrings =
        data.findValues("validating_pubkey").stream()
            .map(node -> node.asText())
            .collect(Collectors.toList());

    Assertions.assertThat(actualKeyStrings).containsOnlyOnceElementsOf(expectedKeyStrings);
    checkReadOnly(data, false);
  }

  private String getValidatorListing() throws IOException {
    final String result = httpClient.get(validatorUri.get(), "/eth/v1/keystores");
    LOG.debug("GET Keys: " + result);
    return result;
  }

  private String addValidators(final ValidatorKeystores validatorKeystores, final Path tempDir)
      throws IOException {
    final List<String> keystores = validatorKeystores.getKeystores(tempDir);
    final List<String> passwords = validatorKeystores.getPasswords();

    final String body =
        jsonProvider.objectToJSON(Map.of("keystores", keystores, "passwords", passwords));

    final String result = httpClient.post(validatorUri.get(), "/eth/v1/keystores", body);
    LOG.debug("POST Keys: " + result);
    return result;
  }

  private String removeValidator(final BLSPublicKey publicKey) throws IOException {
    final String body = jsonProvider.objectToJSON(Map.of("pubkeys", List.of(publicKey.toString())));
    final String result = httpClient.delete(validatorUri.get(), "/eth/v1/keystores", body);
    LOG.debug("DELETE Keys: " + result);
    return result;
  }

  private void checkReadOnly(final JsonNode data, final boolean readOnlyStatus) {
    assertThat(data.isArray()).isTrue();
    for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
      final JsonNode node = it.next();
      assertThat(node.get("readonly").asBoolean()).isEqualTo(readOnlyStatus);
    }
  }

  private void checkStatus(final JsonNode data, final String status) {
    assertThat(data.isArray()).isTrue();
    for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
      final JsonNode node = it.next();
      assertThat(node.get("status").asText()).isEqualTo(status);
    }
  }
}
