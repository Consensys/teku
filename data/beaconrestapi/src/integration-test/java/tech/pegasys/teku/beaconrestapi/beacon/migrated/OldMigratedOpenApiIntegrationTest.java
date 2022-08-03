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

package tech.pegasys.teku.beaconrestapi.beacon.migrated;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.ReflectionBasedBeaconRestApi;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;

public class OldMigratedOpenApiIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private JsonNode currentJsonNodes;
  private final OpenApiTestUtil<OldMigratedOpenApiIntegrationTest> util =
      new OpenApiTestUtil<>(OldMigratedOpenApiIntegrationTest.class);

  @BeforeEach
  public void setup() throws IOException {
    startRestAPIAtGenesis();
    currentJsonNodes =
        util.parseSwagger(((ReflectionBasedBeaconRestApi) beaconRestApi).getMigratedOpenApi());
  }

  @Test
  void schemaObjects_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode schemas = currentJsonNodes.findPath("components").findPath("schemas");
    util.compareToKnownDefinitions(tempDir, "schema", schemas);
  }

  @Test
  void shouldHaveConsistentReferences() {
    util.checkReferences(currentJsonNodes);
  }

  @Test
  void paths_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode paths = currentJsonNodes.findPath("paths");

    util.compareToKnownDefinitions(tempDir, "paths", paths);
  }
}
