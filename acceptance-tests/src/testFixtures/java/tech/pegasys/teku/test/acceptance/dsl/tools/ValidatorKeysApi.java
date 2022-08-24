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

package tech.pegasys.teku.test.acceptance.dsl.tools;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Suppliers;
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.test.acceptance.dsl.SimpleHttpClient;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorKeysApi {
  private static final Logger LOG = LogManager.getLogger();
  private static final String LOCAL_KEYS_URL = "/eth/v1/keystores";

  private static final String LOCAL_FEE_RECIPIENT_URL = "/eth/v1/validator/{pubkey}/feerecipient";

  public static final String LOCAL_GAS_LIMIT_URL = "/eth/v1/validator/{pubkey}/gas_limit";
  private static final String REMOTE_KEYS_URL = "/eth/v1/remotekeys";
  private final JsonProvider jsonProvider = new JsonProvider();
  private final SimpleHttpClient httpClient;
  private final Supplier<URI> validatorUri;
  private final Supplier<String> apiPasswordSupplier;

  public ValidatorKeysApi(
      final SimpleHttpClient httpClient,
      final Supplier<URI> validatorUri,
      final Supplier<String> apiPasswordSupplier) {
    this.httpClient = httpClient;
    this.validatorUri = validatorUri;
    this.apiPasswordSupplier = Suppliers.memoize(apiPasswordSupplier::get);
  }

  public void addLocalValidatorsAndExpect(
      final ValidatorKeystores validatorKeystores, final String expectedStatus) throws IOException {
    final Path tempDir = Files.createTempDirectory("validator-keys-api");
    final JsonNode addResult =
        jsonProvider.getObjectMapper().readTree(addLocalValidators(validatorKeystores, tempDir));
    assertThat(addResult.get("data").size()).isEqualTo(validatorKeystores.getValidatorCount());
    checkStatus(addResult.get("data"), expectedStatus);
    tempDir.toFile().delete();
  }

  public void addFeeRecipient(final BLSPublicKey publicKey, final Eth1Address eth1Address)
      throws IOException {
    addFeeRecipientToValidator(publicKey, eth1Address);
  }

  public void addGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit) throws IOException {

    addGasLimitToValidator(publicKey, gasLimit);
  }

  public void addRemoteValidatorsAndExpect(
      final List<BLSPublicKey> expectedKeys, final String signerUrl, final String expectedStatus)
      throws IOException {
    final JsonNode addResult =
        jsonProvider.getObjectMapper().readTree(addRemoteValidators(expectedKeys, signerUrl));
    assertThat(addResult.get("data").size()).isEqualTo(expectedKeys.size());
    checkStatus(addResult.get("data"), expectedStatus);
  }

  public void removeLocalValidatorAndCheckStatus(
      final BLSPublicKey publicKey, final String expectedStatus) throws IOException {
    final JsonNode removeResult =
        jsonProvider.getObjectMapper().readTree(removeLocalValidator(publicKey));
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

  public void removeRemoteValidatorAndCheckStatus(
      final BLSPublicKey publicKey, final String expectedStatus) throws IOException {
    final JsonNode removeResult =
        jsonProvider.getObjectMapper().readTree(removeRemoteValidator(publicKey));
    assertThat(removeResult.get("data").size()).isEqualTo(1);
    checkStatus(removeResult.get("data"), expectedStatus);
  }

  public void assertLocalValidatorListing(final List<BLSPublicKey> expectedKeys)
      throws IOException {
    final JsonNode result = jsonProvider.getObjectMapper().readTree(getLocalValidatorListing());
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

  public void assertRemoteValidatorListing(final List<BLSPublicKey> expectedKeys)
      throws IOException {
    final JsonNode result = jsonProvider.getObjectMapper().readTree(getRemoteValidatorListing());
    final JsonNode data = result.get("data");
    assertThat(data.isArray()).isTrue();
    final List<String> expectedKeyStrings =
        expectedKeys.stream()
            .map(key -> key.toBytesCompressed().toHexString())
            .collect(Collectors.toList());
    final List<String> actualKeyStrings =
        data.findValues("pubkey").stream().map(node -> node.asText()).collect(Collectors.toList());

    Assertions.assertThat(actualKeyStrings).containsOnlyOnceElementsOf(expectedKeyStrings);
    checkReadOnly(data, false);
  }

  public URI getValidatorUri() {
    return validatorUri.get();
  }

  private String getRemoteValidatorListing() throws IOException {
    final String result = httpClient.get(validatorUri.get(), REMOTE_KEYS_URL, authHeaders());
    LOG.debug("GET REMOTE Keys: " + result);
    return result;
  }

  private String getLocalValidatorListing() throws IOException {
    final String result = httpClient.get(validatorUri.get(), LOCAL_KEYS_URL, authHeaders());
    LOG.debug("GET LOCAL Keys: " + result);
    return result;
  }

  public void assertValidatorFeeRecipient(
      final BLSPublicKey publicKey, final String expectedEthAddress) throws IOException {

    final String result =
        jsonProvider
            .getObjectMapper()
            .readTree(getLocalFeeRecipient(publicKey))
            .get("data")
            .get("ethaddress")
            .asText();
    assertThat(result).isEqualTo(expectedEthAddress);
  }

  public void assertValidatorGasLimit(final BLSPublicKey publicKey, final UInt64 expectedGasLimit)
      throws IOException {
    final String result =
        jsonProvider
            .getObjectMapper()
            .readTree(getLocalGasLimit(publicKey))
            .get("data")
            .get("gas_limit")
            .asText();
    final UInt64 gasLimit = UInt64.valueOf(result);
    assertThat(gasLimit).isEqualTo(expectedGasLimit);
  }

  private Map<String, String> authHeaders() {
    if (apiPasswordSupplier.get().isEmpty()) {
      LOG.debug("Not using auth headers");
      return Map.of();
    }
    return Map.of("Authorization", "Bearer " + apiPasswordSupplier.get());
  }

  private String getLocalFeeRecipient(final BLSPublicKey publicKey) throws IOException {
    return httpClient.get(validatorUri.get(), getFeeRecipientUrl(publicKey), authHeaders());
  }

  private String getLocalGasLimit(final BLSPublicKey publicKey) throws IOException {
    return httpClient.get(validatorUri.get(), getGasLimitUrl(publicKey), authHeaders());
  }

  private String addLocalValidators(final ValidatorKeystores validatorKeystores, final Path tempDir)
      throws IOException {
    final List<String> keystores = validatorKeystores.getKeystores(tempDir);
    final List<String> passwords = validatorKeystores.getPasswords();

    final String body =
        jsonProvider.objectToJSON(Map.of("keystores", keystores, "passwords", passwords));

    final String result = httpClient.post(validatorUri.get(), LOCAL_KEYS_URL, body, authHeaders());
    LOG.debug("POST Keys: " + result);
    return result;
  }

  private void addFeeRecipientToValidator(
      final BLSPublicKey publicKey, final Eth1Address feeRecipient) throws IOException {

    final String body = jsonProvider.objectToJSON(Map.of("ethaddress", feeRecipient.toHexString()));

    final String result =
        httpClient.post(validatorUri.get(), getFeeRecipientUrl(publicKey), body, authHeaders());
    LOG.debug("POST Fee Recipient: " + result);
  }

  private void addGasLimitToValidator(final BLSPublicKey publicKey, final UInt64 gasLimit)
      throws IOException {

    final String body = jsonProvider.objectToJSON(Map.of("gas_limit", gasLimit.toString()));

    final String result =
        httpClient.post(validatorUri.get(), getGasLimitUrl(publicKey), body, authHeaders());
    LOG.debug("POST gas limit: " + result);
  }

  private String addRemoteValidators(final List<BLSPublicKey> publicKeys, final String signerUrl)
      throws IOException {

    List<Map<String, String>> requestPayload =
        publicKeys.stream()
            .map(k -> remotePostRequestBody(k, signerUrl))
            .collect(Collectors.toList());
    final String body = jsonProvider.objectToJSON(Map.of("remote_keys", requestPayload));

    final String result = httpClient.post(validatorUri.get(), REMOTE_KEYS_URL, body, authHeaders());
    LOG.debug("POST REMOTE Keys: " + result);
    return result;
  }

  private Map<String, String> remotePostRequestBody(
      final BLSPublicKey publicKey, final String signerUrl) {
    return Map.of("pubkey", publicKey.toString(), "url", signerUrl);
  }

  private String removeLocalValidator(final BLSPublicKey publicKey) throws IOException {
    final String body = jsonProvider.objectToJSON(Map.of("pubkeys", List.of(publicKey.toString())));
    final String result =
        httpClient.delete(validatorUri.get(), LOCAL_KEYS_URL, body, authHeaders());
    LOG.debug("DELETE LOCAL Keys: " + result);
    return result;
  }

  private String removeRemoteValidator(final BLSPublicKey publicKey) throws IOException {
    final String body = jsonProvider.objectToJSON(Map.of("pubkeys", List.of(publicKey.toString())));
    final String result =
        httpClient.delete(validatorUri.get(), REMOTE_KEYS_URL, body, authHeaders());
    LOG.debug("DELETE REMOTE Keys: " + result);
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

  private String getFeeRecipientUrl(final BLSPublicKey publicKey) {
    return LOCAL_FEE_RECIPIENT_URL.replace("{pubkey}", publicKey.toHexString());
  }

  private String getGasLimitUrl(final BLSPublicKey publicKey) {
    return LOCAL_GAS_LIMIT_URL.replace("{pubkey}", publicKey.toHexString());
  }
}
