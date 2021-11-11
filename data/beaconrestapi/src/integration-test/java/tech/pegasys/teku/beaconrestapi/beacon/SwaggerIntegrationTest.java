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

package tech.pegasys.teku.beaconrestapi.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;

public class SwaggerIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private JsonNode currentJsonNodes;

  @BeforeEach
  public void setup() throws IOException {
    startRestAPIAtGenesis();
    final Response response = get();
    currentJsonNodes = OBJECT_MAPPER.readTree(response.body().string());
  }

  @Test
  void schemaObjects_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode schemas = currentJsonNodes.findPath("components").findPath("schemas");

    List<String> fieldNames = ImmutableList.copyOf(schemas.fieldNames());
    for (String current : fieldNames) {
      checkStructureIsConsistent("schema", tempDir, current, schemas.path(current));
    }
    checkAllExpectedFilesExist(tempDir, "schema");
  }

  @Test
  void paths_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode paths = currentJsonNodes.findPath("paths");

    List<String> fieldNames = ImmutableList.copyOf(paths.fieldNames());
    for (String current : fieldNames) {
      checkStructureIsConsistent("paths", tempDir, current, paths.path(current));
    }

    checkAllExpectedFilesExist(tempDir, "paths");
  }

  private void checkAllExpectedFilesExist(final Path tempDir, final String schema)
      throws IOException {
    final List<String> expectedFileListing =
        Arrays.asList(
            Resources.toString(
                    Resources.getResource(SwaggerIntegrationTest.class, schema + "_listing.txt"),
                    UTF_8)
                .split("\n"));
    for (String file : expectedFileListing) {
      assertThat(tempDir.resolve(file).toFile()).exists();
    }
  }

  private void checkStructureIsConsistent(
      final String namespace, final Path tempDir, final String elementName, final JsonNode node)
      throws IOException {
    final String filename = elementName.replaceAll("/", "_") + ".json";
    Files.write(tempDir.resolve(filename), prettyJson(node).getBytes(UTF_8));
    final JsonNode expectedNode = loadResourceFile(namespace + "/" + filename);
    assertThat(node)
        .withFailMessage(
            String.format(
                "Expected structure of %s: %s to match that stored in %s:%s",
                elementName,
                prettyJson(node),
                namespace + "/" + filename,
                prettyJson(expectedNode)))
        .isEqualTo(expectedNode);
  }

  private String prettyJson(final JsonNode jsonNode) throws JsonProcessingException {
    System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
  }

  private JsonNode loadResourceFile(final String resourceFile) throws IOException {
    final String resource =
        Resources.toString(
            Resources.getResource(SwaggerIntegrationTest.class, resourceFile), UTF_8);
    return OBJECT_MAPPER.readTree(resource);
  }

  public Response get() throws IOException {
    return getResponse("/swagger-docs");
  }
}
