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

package tech.pegasys.teku.validator.remote.sentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.provider.JsonProvider;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class SentryNodesConfigLoaderTest {

  private JsonProvider jsonProvider;
  private ResourceLoader resourceLoader;

  private SentryNodesConfigLoader configLoader;

  @BeforeEach
  public void before() {
    jsonProvider = spy(new JsonProvider());
    jsonProvider.getObjectMapper().configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
    resourceLoader = mock(ResourceLoader.class);

    configLoader = new SentryNodesConfigLoader(jsonProvider, resourceLoader);
  }

  @AfterEach
  public void teardown() {
    Mockito.reset(jsonProvider, resourceLoader);
  }

  @Test
  public void shouldLoadConfigWithCorrectValues() throws Exception {
    mockResourceLoaderWithJsonConfig("fullConfig", SENTRY_NODE_FULL_JSON_CONFIG);

    final SentryNodesConfig config = configLoader.load("fullConfig");

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig().get().getEndpoints())
        .contains("http://block:5051");
    assertThat(config.getAttestationPublisherConfig().get().getEndpoints())
        .contains("http://attestation:5051");
  }

  @Test
  public void shouldLoadConfigOnlyWithDutiesProvider() throws Exception {
    mockResourceLoaderWithJsonConfig("minimumConfig", SENTRY_NODE_MINIMUM_JSON_CONFIG);

    final SentryNodesConfig config = configLoader.load("minimumConfig");

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig()).isEmpty();
    assertThat(config.getAttestationPublisherConfig()).isEmpty();
  }

  @Test
  public void shouldFailLoadingConfigMissingNestedEndpoints() throws Exception {
    mockResourceLoaderWithJsonConfig("missingEndpoints", SENTRY_NODE_MISSING_ENDPOINTS_JSON_CONFIG);

    assertThatThrownBy(() -> configLoader.load("missingEndpoints"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(JsonProcessingException.class)
        .hasMessage("Invalid sentry nodes configuration file");
  }

  @Test
  public void shouldFailLoadingConfigWhenMissingRequiredDutiesProvider() throws Exception {
    mockResourceLoaderWithJsonConfig(
        "configWithoutDutiesProvider", SENTRY_NODE_MISSING_DUTIES_PROVIDER_JSON_CONFIG);

    assertThatThrownBy(() -> configLoader.load("configWithoutDutiesProvider"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(JsonProcessingException.class)
        .hasMessage("Invalid sentry nodes configuration file");
  }

  @Test
  public void shouldLoadConfigSuccessfullyWhenMissingOptionalBlockHandler() throws Exception {
    mockResourceLoaderWithJsonConfig(
        "configWithoutBlockHandler", SENTRY_NODE_MISSING_BLOCK_HANDLER_JSON_CONFIG);

    final SentryNodesConfig config = configLoader.load("configWithoutBlockHandler");

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getAttestationPublisherConfig().get().getEndpoints())
        .contains("http://attestation:5051");
    assertThat(config.getBlockHandlerNodeConfig()).isEmpty();
  }

  @Test
  public void shouldLoadConfigSuccessfullyWhenMissingOptionalAttestationPublisher()
      throws Exception {
    mockResourceLoaderWithJsonConfig(
        "configWithoutAttestationPublisher", SENTRY_NODE_MISSING_ATTESTATION_PUBLISHER_JSON_CONFIG);
    final SentryNodesConfig config = configLoader.load("configWithoutAttestationPublisher");

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig().get().getEndpoints())
        .contains("http://block:5051");
    assertThat(config.getAttestationPublisherConfig()).isEmpty();
  }

  @Test
  public void shouldRethrowExceptionWhenUnexpectedErrorParsingFile() throws Exception {
    when(resourceLoader.loadBytes(anyString())).thenReturn(Optional.of(Bytes.EMPTY));
    doThrow(new RuntimeException("an error")).when(jsonProvider).jsonToObject(anyString(), any());

    assertThatThrownBy(() -> configLoader.load("config"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessage("Unexpected error parsing sentry nodes configuration file");
  }

  @Test
  public void shouldRethrowExceptionWhenResourceLoaderFails() throws Exception {
    when(resourceLoader.loadBytes(anyString())).thenThrow(new IOException("an error"));

    assertThatThrownBy(() -> configLoader.load("config"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IOException.class)
        .hasMessage("Error loading sentry nodes configuration file from config");
  }

  private void mockResourceLoaderWithJsonConfig(final String resourceName, final String json)
      throws IOException {
    when(resourceLoader.loadBytes(eq(resourceName)))
        .thenReturn(Optional.of(Bytes.wrap(json.getBytes(StandardCharsets.UTF_8))));
  }

  // region Json Configs
  private static final String SENTRY_NODE_FULL_JSON_CONFIG =
      "{\n"
          + "  \"beacon_nodes\": {\n"
          + "    \"duties_provider\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://duties:5051\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"block_handler\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://block:5051\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"attestation_publisher\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://attestation:5051\"\n"
          + "      ]\n"
          + "    }\n"
          + "  }\n"
          + "}";

  private static final String SENTRY_NODE_MISSING_BLOCK_HANDLER_JSON_CONFIG =
      "{\n"
          + "  \"beacon_nodes\": {\n"
          + "    \"duties_provider\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://duties:5051\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"attestation_publisher\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://attestation:5051\"\n"
          + "      ]\n"
          + "    }\n"
          + "  }\n"
          + "}";

  private static final String SENTRY_NODE_MISSING_ATTESTATION_PUBLISHER_JSON_CONFIG =
      "{\n"
          + "  \"beacon_nodes\": {\n"
          + "    \"duties_provider\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://duties:5051\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"block_handler\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://block:5051\"\n"
          + "      ]\n"
          + "    }\n"
          + "  }\n"
          + "}";

  private static final String SENTRY_NODE_MISSING_DUTIES_PROVIDER_JSON_CONFIG =
      "{\n"
          + "  \"beacon_nodes\": {\n"
          + "    \"block_handler\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://block:5051\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"attestation_publisher\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://attestation:5051\"\n"
          + "      ]\n"
          + "    }\n"
          + "  }\n"
          + "}";

  private static final String SENTRY_NODE_MINIMUM_JSON_CONFIG =
      "{\n"
          + "  \"beacon_nodes\": {\n"
          + "    \"duties_provider\": {\n"
          + "      \"endpoints\": [\n"
          + "        \"http://duties:5051\"\n"
          + "      ]\n"
          + "    }"
          + "  }\n"
          + "}";

  private static final String SENTRY_NODE_MISSING_ENDPOINTS_JSON_CONFIG =
      "{\"beacon_nodes\": {\"duties_provider\": {}}}";
  // endregion

}
