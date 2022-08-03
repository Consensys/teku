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

import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import kotlin.jvm.functions.Function1;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocBuilder;

public class SwaggerBuilder {
  private static final Logger LOG = LogManager.getLogger();

  public static final String RESOURCES_WEBJARS_SWAGGER_UI =
      "/META-INF/resources/webjars/swagger-ui/";
  private static final String SWAGGER_UI_PATH = "/swagger-ui";
  private static final String SWAGGER_HOSTED_PATH = "/webjars/swagger-ui";
  // Be careful when modifying this, it's used in static js files for serving Swagger UI
  private static final String SWAGGER_DOCS_PATH = "/swagger-docs";
  private static final Set<String> MODIFIED_FILES =
      Set.of(
          SWAGGER_HOSTED_PATH + "/swagger-initializer.js",
          SWAGGER_HOSTED_PATH + "/swagger-initializer.js.gz");

  private final boolean enabled;

  public SwaggerBuilder(final boolean enabled) {
    this.enabled = enabled;
  }

  public void configureUI(final JavalinConfig config) {
    if (!enabled) {
      return;
    }
    List<String> swaggerVersionPaths = listClasspathDir(RESOURCES_WEBJARS_SWAGGER_UI);
    if (swaggerVersionPaths.size() != 1) {
      throw new InvalidConfigurationException(
          String.format(
              "Swagger UI not found or several versions found in paths: %s",
              String.join(",", swaggerVersionPaths)));
    }
    config.addStaticFiles(
        staticFileConfig -> {
          staticFileConfig.hostedPath = SWAGGER_HOSTED_PATH;
          staticFileConfig.directory = swaggerVersionPaths.get(0);
          staticFileConfig.location = Location.CLASSPATH;
          staticFileConfig.skipFileFunction =
              (Function1<HttpServletRequest, Boolean>)
                  httpServletRequest ->
                      httpServletRequest.getPathInfo() != null
                          && MODIFIED_FILES.contains(httpServletRequest.getPathInfo());
        });
    config.addSinglePageRoot(
        SWAGGER_HOSTED_PATH + "/swagger-initializer.js",
        "/swagger-ui/patched/swagger-initializer.js",
        Location.CLASSPATH);
    config.addSinglePageRoot(
        SWAGGER_HOSTED_PATH + "/swagger-initializer.js.gz",
        "/swagger-ui/patched/swagger-initializer.js.gz",
        Location.CLASSPATH);
    config.addSinglePageRoot(SWAGGER_UI_PATH, "/swagger-ui/patched/index.html", Location.CLASSPATH);
    config.addSinglePageRoot(
        SWAGGER_UI_PATH + ".gz", "/swagger-ui/patched/index.html.gz", Location.CLASSPATH);
  }

  public Optional<String> configureDocs(
      final Javalin app, final OpenApiDocBuilder openApiDocBuilder) {
    if (!enabled) {
      return Optional.empty();
    }
    final String apiDocs = openApiDocBuilder.build();
    app.get(SWAGGER_DOCS_PATH, ctx -> ctx.json(apiDocs));
    return Optional.of(apiDocs);
  }

  public static List<String> listClasspathDir(final String path) {
    try {
      final URI uri = SwaggerBuilder.class.getResource(path).toURI();
      if (uri.getScheme().equals("jar")) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
          return listPath(fileSystem.getPath(path));
        }
      } else {
        return listPath(Paths.get(uri));
      }
    } catch (Exception ex) {
      LOG.error("Cannot get \"Swagger-UI\" version information", ex);
      throw new InvalidConfigurationException(ex);
    }
  }

  private static List<String> listPath(final Path path) throws IOException {
    try (Stream<Path> stream = Files.list(path)) {
      return stream.map(Path::toString).collect(Collectors.toList());
    }
  }
}
