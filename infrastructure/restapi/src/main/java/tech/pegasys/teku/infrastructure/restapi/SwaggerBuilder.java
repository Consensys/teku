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
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import kotlin.jvm.functions.Function1;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocBuilder;

public class SwaggerBuilder {
  // Version here MUST match `swagger-ui` library version
  private static final String SWAGGER_UI_VERSION = "4.10.3";

  private static final String SWAGGER_UI_PATH = "/swagger-ui";
  private static final String SWAGGER_HOSTED_PATH = "/webjars/swagger-ui/" + SWAGGER_UI_VERSION;
  // Be careful when modifying this, it's used in static js files for serving Swagger UI
  private static final String SWAGGER_DOCS_PATH = "/swagger-docs";
  private static final Set<String> MODIFIED_FILES =
      Set.of(SWAGGER_HOSTED_PATH + "/swagger-initializer.js");

  public static final String RESOURCES_WEBJARS_SWAGGER_UI =
      "/META-INF/resources/webjars/swagger-ui/" + SWAGGER_UI_VERSION + "/";
  private static final String SWAGGER_UI_PATCHED = "/swagger-ui/patched/";

  private static final Handler INDEX =
      (ctx) -> {
        Map<String, Object> model = new HashMap<>();
        model.put("title", "Teku REST API");
        model.put("basePath", SWAGGER_HOSTED_PATH);
        ctx.render("index.html", model);
      };

  private final boolean enabled;

  public SwaggerBuilder(final boolean enabled) {
    this.enabled = enabled;
  }

  public void configureUI(final JavalinConfig config) {
    if (!enabled) {
      return;
    }
    config.addStaticFiles(
        staticFileConfig -> {
          staticFileConfig.hostedPath = SWAGGER_HOSTED_PATH;
          staticFileConfig.directory = RESOURCES_WEBJARS_SWAGGER_UI;
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
    ThymeleafConfigurator.enableThymeleafTemplates(SWAGGER_UI_PATCHED);
    config.addSinglePageHandler(SWAGGER_UI_PATH, INDEX);
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
}
