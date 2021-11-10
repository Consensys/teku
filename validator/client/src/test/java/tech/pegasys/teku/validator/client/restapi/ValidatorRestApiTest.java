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
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;

class ValidatorRestApiTest {
  private final RestApiBuilder builder = new RestApiBuilder();

  @Test
  void shouldHaveReferencesInOpenApiDoc() throws JsonProcessingException {
    final ValidatorRestApiConfig config = mock(ValidatorRestApiConfig.class);
    when(config.getRestApiInterface()).thenReturn("127.1.1.1");
    ValidatorRestApi.create(config, List::of, builder);
    final String json = builder.getOpenApiDocument();
    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode jsonNode = objectMapper.readTree(json);
    checkReferences(jsonNode);
  }

  private void checkReferences(final JsonNode jsonNode) {
    for (JsonNode node : jsonNode.findValues("$ref")) {
      System.out.println(node.asText());
      assertThat(node.asText())
          .withFailMessage("Did not start with '#/' " + node.asText())
          .startsWith("#/");
      assertThat(pathExists(jsonNode, node.asText().substring(2)))
          .withFailMessage("Missing reference " + node.asText())
          .isTrue();
    }
  }

  private boolean pathExists(final JsonNode jsonNode, final String path) {
    JsonNode node;
    if (path.contains("/")) {
      node = jsonNode.path(path.substring(0, path.indexOf("/")));
      if (node.isMissingNode()) {
        return false;
      }
      return pathExists(node, path.substring(path.indexOf("/") + 1));
    }
    return !jsonNode.path(path).isMissingNode();
  }
}
