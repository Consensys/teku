/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequestImpl;

public abstract class MigratingEndpointAdapter extends RestApiEndpoint implements Handler {

  protected MigratingEndpointAdapter(final EndpointMetadata metadata) {
    super(metadata);
  }

  public void addEndpoint(final Javalin app) {
    final EndpointMetadata metadata = getMetadata();
    app.addHandler(metadata.getMethod(), metadata.getPath(), this);
  }

  protected void adapt(final Context ctx) throws Exception {
    final RestApiRequest request = new RestApiRequestImpl(ctx, getMetadata());
    handleRequest(request);
  }
}
