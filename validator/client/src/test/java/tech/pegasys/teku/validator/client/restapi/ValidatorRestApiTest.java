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

package tech.pegasys.teku.validator.client.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.validator.client.KeyManager;

class ValidatorRestApiTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ValidatorRestApiConfig config = mock(ValidatorRestApiConfig.class);
  private final KeyManager keyManager = mock(KeyManager.class);
  private RestApi restApi;

  @BeforeEach
  void setup() {
    when(config.getRestApiInterface()).thenReturn("127.1.1.1");
    when(config.isRestApiDocsEnabled()).thenReturn(true);
    restApi = ValidatorRestApi.create(config, keyManager);
  }

  @Test
  void shouldHaveReferencesInOpenApiDoc() throws JsonProcessingException {
    final Optional<String> maybeJson = restApi.getRestApiDocs();
    assertThat(maybeJson).isPresent();
    final JsonNode jsonNode = objectMapper.readTree(maybeJson.orElseThrow());
    checkReferences(jsonNode);
  }

  private void checkReferences(final JsonNode jsonNode) {
    for (JsonNode node : jsonNode.findValues("$ref")) {
      assertThat(node.asText())
          .withFailMessage("Did not start with '#/' " + node.asText())
          .startsWith("#/");
      final List<JsonNode> found = getNodesAtPath(jsonNode, node.asText().substring(2));
      assertThat(found.isEmpty()).withFailMessage("Missing reference " + node.asText()).isFalse();
      assertThat(found.size())
          .withFailMessage("Multiple schema objects satisfy reference " + node.asText())
          .isEqualTo(1);
    }
  }

  private List<JsonNode> getNodesAtPath(final JsonNode jsonNode, final String path) {
    JsonNode node;
    if (path.contains("/")) {
      node = jsonNode.path(path.substring(0, path.indexOf("/")));
      if (node.isMissingNode()) {
        return Collections.emptyList();
      }
      return getNodesAtPath(node, path.substring(path.indexOf("/") + 1));
    }
    return jsonNode.path(path).isMissingNode()
        ? Collections.emptyList()
        : jsonNode.findValues(path);
  }
}
