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

import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.JSON_CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.restapi.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

public class RestApiRequest {
  private final Context context;
  private final EndpointMetadata metadata;

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() throws JsonProcessingException {
    DeserializableTypeDefinition<T> bodySchema =
        (DeserializableTypeDefinition<T>) metadata.getRequestBodyType();

    final String body = context.body();
    final T result = JsonUtil.parse(body, bodySchema);
    if (result == null) {
      throw new MissingRequestBodyException();
    }
    return result;
  }

  public RestApiRequest(final Context context, final EndpointMetadata metadata) {
    this.context = context;
    this.metadata = metadata;
  }

  public void respondOk(final Object response) throws JsonProcessingException {
    respond(HttpStatusCodes.SC_OK, JSON_CONTENT_TYPE, response);
  }

  public void respondError(final int statusCode, final String message)
      throws JsonProcessingException {
    respond(statusCode, JSON_CONTENT_TYPE, new HttpErrorResponse(statusCode, message));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void respond(final int statusCode, final String contentType, final Object response)
      throws JsonProcessingException {
    final SerializableTypeDefinition type = metadata.getResponseType(statusCode, contentType);
    context.status(statusCode).result(JsonUtil.serialize(response, type));
  }
}
