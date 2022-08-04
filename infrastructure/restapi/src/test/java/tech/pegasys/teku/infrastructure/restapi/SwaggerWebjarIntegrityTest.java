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

package tech.pegasys.teku.infrastructure.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.restapi.SwaggerBuilder.RESOURCES_WEBJARS_SWAGGER_UI;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class SwaggerWebjarIntegrityTest {
  private static final String VENDOR_COPY_PATH = "/swagger-ui/vendor";
  /**
   * `infrastructure/restapi/src/main/resources/swagger-ui/vendor` contains several files which
   * should match files with the same names in 'org.webjars:swagger-ui'.
   *
   * <p>NOTICE: If it doesn't match due to swagger version update, both
   * `infrastructure/restapi/src/main/resources/swagger-ui/vendor` (just jar files copy) and
   * `infrastructure/restapi/src/main/resources/swagger-ui/patched` (uptodate version files with
   * changed links) should be updated
   */
  @Test
  public void shouldHaveTheSameVersionOfFilesInVendorFolderAndSwaggerJar() throws IOException {
    final String swaggerJarPath = getSwaggerJarPath();
    final List<String> vendorFiles = SwaggerBuilder.listClasspathDir(VENDOR_COPY_PATH);
    for (String filePath : vendorFiles) {
      final byte[] resourceFileContents = getClasspathFile(filePath);
      final byte[] jarFileContents =
          getClasspathFile(swaggerJarPath + "/" + FilenameUtils.getName(filePath));

      assertThat(jarFileContents).isEqualTo(resourceFileContents);
    }
  }

  public byte[] getClasspathFile(final String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return in.readAllBytes();
    }
  }

  private String getSwaggerJarPath() {
    List<String> swaggerVersionPaths =
        SwaggerBuilder.listClasspathDir(RESOURCES_WEBJARS_SWAGGER_UI);
    if (swaggerVersionPaths.size() != 1) {
      throw new InvalidConfigurationException(
          String.format(
              "Swagger UI not found or several versions found in paths: %s",
              String.join(",", swaggerVersionPaths)));
    }
    return swaggerVersionPaths.get(0);
  }
}
