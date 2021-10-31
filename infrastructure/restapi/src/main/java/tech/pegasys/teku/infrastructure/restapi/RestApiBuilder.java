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

package tech.pegasys.teku.infrastructure.restapi;

import static tech.pegasys.teku.infrastructure.restapi.types.PrimitiveTypeDefinition.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.types.PrimitiveTypeDefinition.UINT64_TYPE;

import io.javalin.Javalin;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jetty.server.Server;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocHandler;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiModel;
import tech.pegasys.teku.infrastructure.restapi.types.TypeDefinition;

public class RestApiBuilder {

  private int port;
  private String listenAddress;
  private boolean apiDocsEnabled = false;
  private final Map<String, String> tags = new HashMap<>();
  private String contactName;
  private String contactUrl;
  private String version;
  private String title;
  private String description;
  private String licenseName;
  private String licenseUrl;

  private final List<ApiDefinition<?>> apis = new ArrayList<>();

  public RestApiBuilder port(final int port) {
    this.port = port;
    return this;
  }

  // ********* OpenAPI Info Section *************

  public RestApiBuilder title(final String title) {
    this.title = title;
    return this;
  }

  public RestApiBuilder description(final String description) {
    this.description = description;
    return this;
  }

  public RestApiBuilder version(final String version) {
    this.version = version;
    return this;
  }

  public RestApiBuilder contact(final String contactName, final String contactUrl) {
    this.contactName = contactName;
    this.contactUrl = contactUrl;
    return this;
  }

  public RestApiBuilder license(final String licenseName, final String licenseUrl) {
    this.licenseName = licenseName;
    this.licenseUrl = licenseUrl;
    return this;
  }

  public RestApiBuilder listenAddress(final String listenAddress) {
    this.listenAddress = listenAddress;
    return this;
  }

  public RestApiBuilder apiDocsEnabled(final boolean apiDocsEnabled) {
    this.apiDocsEnabled = apiDocsEnabled;
    return this;
  }

  public RestApiBuilder tag(final String name, final String description) {
    tags.put(name, description);
    return this;
  }

  public RestApiBuilder addHandler(final ApiDefinition<?> apiDefinition) {
    apis.add(apiDefinition);
    return this;
  }

  public RestApiDefinition build() {
    final Javalin app =
        Javalin.create(
            config -> {
              config.defaultContentType = "application/json";
              config.showJavalinBanner = false;
              // CORS Stuff...
              config.server(
                  () -> new Server(InetSocketAddress.createUnresolved(listenAddress, port)));
            });
    // Add AllowHost stuff
    // Add exception handlers?

    apis.forEach(api -> app.addHandler(api.getHandlerType(), api.getPath(), api));
    if (apiDocsEnabled) {
      final OpenApiModel model = OpenApiModel.createFrom(apis);
      app.get("/swagger-docs", new OpenApiDocHandler(model));
    }
    return new RestApiDefinition(app);
  }

  public static void main(String[] args) {
    final RestApiDefinition api =
        new RestApiBuilder()
            .port(5051)
            .listenAddress("127.0.0.1")
            .apiDocsEnabled(true)
            .title("Teku")
            .addHandler(new ExampleApi())
            .build();
    api.start().join();
  }

  public static class ExampleApi extends ApiDefinition<Void> {
    public ExampleApi() {
      super(
          get("/eth/v1/foo/{ack}")
              .summary("Get the current foo from the bar")
              .description("Retrieves whatever the current foo is considering the value of bar")
              .tags("a", "b", "c")
              .queryParam("bar", "Type reference bar", STRING_TYPE, false)
              .queryParam("epoch", "The epoch", UINT64_TYPE, true)
              .pathParam("ack", "The ack", TypeDefinition.listOf(STRING_TYPE))
              .response(404, "No foos found")
              .response(
                  200,
                  "The requested foo",
                  TypeDefinition.<String>object("foo")
                      .withField("x", STRING_TYPE, Function.identity())
                      .build())
              .build());
    }

    @Override
    public void handleRequest() throws Exception {}
  }
}
