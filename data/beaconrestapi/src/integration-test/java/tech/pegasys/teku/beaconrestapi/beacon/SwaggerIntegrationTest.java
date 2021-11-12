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
import java.util.List;
import okhttp3.Response;
import org.assertj.core.api.Fail;
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
    final Response response = getOpanApiDoc();
    currentJsonNodes = OBJECT_MAPPER.readTree(response.body().string());
  }

  @Test
  void schemaObjects_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode schemas = currentJsonNodes.findPath("components").findPath("schemas");
    shouldBeConsistent(tempDir, "schema", schemas);
  }

  @Test
  void paths_shouldBeConsistent(@TempDir final Path tempDir) throws IOException {
    final JsonNode paths = currentJsonNodes.findPath("paths");

    shouldBeConsistent(tempDir, "paths", paths);
  }

  // This test has 2 parts
  // 1. Go through all the objects in our swagger structure and write them to file, comparing to
  //    expected as we go
  //    - this will catch any changes, which if correct should be updated in the resource file
  //    - will also catch NEW objects, and these new objects will need to be added to the
  //      resources for the test, as well as added to the *_listings.txt, so that future removal is
  //      protected.
  // 2. Go through the list of resources (*_listings.txt) and make sure we've created all of those
  //    resources
  //    - this will catch any REMOVED items, and if they should be removed, then the resource list
  //      would need updating and the file can be cleaned up also.

  void shouldBeConsistent(final Path tempDir, final String path, final JsonNode parentNode)
      throws IOException {
    List<String> fieldNames = ImmutableList.copyOf(parentNode.fieldNames());
    for (String current : fieldNames) {
      checkStructureIsConsistent(path, tempDir, current, parentNode.path(current));
    }

    checkAllExpectedFilesExist(tempDir, path);
  }

  private void checkAllExpectedFilesExist(final Path tempDir, final String schema)
      throws IOException {
    final List<String> expectedFileListing =
        Resources.readLines(
            Resources.getResource(SwaggerIntegrationTest.class, schema + "_listing.txt"), UTF_8);
    for (String file : expectedFileListing) {
      assertThat(tempDir.resolve(file).toFile()).exists();
    }
  }

  private void checkStructureIsConsistent(
      final String namespace, final Path tempDir, final String elementName, final JsonNode node)
      throws IOException {
    final String filename = elementName.replaceAll("/", "_") + ".json";
    Files.write(tempDir.resolve(filename), prettyJson(node).getBytes(UTF_8));
    JsonNode expectedNode = null;
    try {
      expectedNode = loadResourceFile(namespace + "/" + filename);
    } catch (Exception e) {
      Fail.fail(
          String.format(
              "Expected to find %s with content %s, does it need to be added?",
              namespace + "/" + filename, prettyJson(node)));
    }
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
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
  }

  private JsonNode loadResourceFile(final String resourceFile) throws IOException {
    final String resource =
        Resources.toString(
            Resources.getResource(SwaggerIntegrationTest.class, resourceFile), UTF_8);
    return OBJECT_MAPPER.readTree(resource);
  }

  public Response getOpanApiDoc() throws IOException {
    return getResponse("/swagger-docs");
  }
}
