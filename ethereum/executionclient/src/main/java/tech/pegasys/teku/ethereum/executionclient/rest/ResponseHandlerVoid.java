/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.ethereum.executionclient.rest;

import java.io.IOException;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class ResponseHandlerVoid extends AbstractResponseHandler {
  private static final Logger LOG = LogManager.getLogger();

  final SafeFuture<Response<Void>> futureResponse = new SafeFuture<>();

  @Override
  void handleFailure(final IOException exception) {
    futureResponse.completeExceptionally(exception);
  }

  @Override
  void handleResponse(final Request request, final okhttp3.Response response) {
    if (handleResponseError(request, response, futureResponse)) {
      return;
    }
    try (final ResponseBody responseBody = response.body()) {
      if (!bodyIsEmpty(responseBody)) {
        LOG.warn("Response body was not empty [{}]", request.url());
      }
      futureResponse.complete(Response.fromNullPayload());
    } catch (final Throwable ex) {
      futureResponse.completeExceptionally(ex);
    }
  }

  public SafeFuture<Response<Void>> getFutureResponse() {
    return futureResponse;
  }
}
