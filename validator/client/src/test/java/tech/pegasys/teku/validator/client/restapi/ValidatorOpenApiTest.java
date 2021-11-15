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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.validator.client.KeyManager;

class ValidatorOpenApiTest {
  private final ValidatorRestApiConfig config = mock(ValidatorRestApiConfig.class);
  private final KeyManager keyManager = mock(KeyManager.class);
  private RestApi restApi;
  private final OpenApiTestUtil<ValidatorOpenApiTest> util =
      new OpenApiTestUtil<>(ValidatorOpenApiTest.class);
  private Optional<String> maybeJson;
  private JsonNode jsonNode;

  @BeforeEach
  void setup() throws JsonProcessingException {
    when(config.getRestApiInterface()).thenReturn("127.1.1.1");
    when(config.isRestApiDocsEnabled()).thenReturn(true);
    restApi = ValidatorRestApi.create(config, keyManager);
    maybeJson = restApi.getRestApiDocs();
    assertThat(maybeJson).isPresent();
    jsonNode = util.parseSwagger(maybeJson.orElseThrow());
  }

  @Test
  void shouldHaveReferencesInOpenApiDoc() {
    util.checkReferences(jsonNode);
  }

  @Test
  void schemaObjects_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode schemas = jsonNode.findPath("components").findPath("schemas");
    util.compareToKnownDefinitions(tempDir, "schema", schemas);
  }

  @Test
  void paths_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode paths = jsonNode.findPath("paths");

    util.compareToKnownDefinitions(tempDir, "paths", paths);
  }
}
