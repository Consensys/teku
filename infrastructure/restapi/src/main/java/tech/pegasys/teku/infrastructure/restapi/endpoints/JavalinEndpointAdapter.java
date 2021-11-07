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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class JavalinEndpointAdapter implements Handler {

  private final RestApiEndpoint endpoint;

  private JavalinEndpointAdapter(final RestApiEndpoint endpoint) {
    this.endpoint = endpoint;
  }

  public static void addEndpoint(final Javalin app, final RestApiEndpoint endpoint) {
    final EndpointMetadata metadata = endpoint.getMetadata();
    app.addHandler(metadata.getMethod(), metadata.getPath(), new JavalinEndpointAdapter(endpoint));
  }

  @Override
  public void handle(final Context ctx) throws Exception {
    final RestApiRequest request = new RestApiRequest(ctx, endpoint.getMetadata());
    endpoint.handle(request);
  }
}
