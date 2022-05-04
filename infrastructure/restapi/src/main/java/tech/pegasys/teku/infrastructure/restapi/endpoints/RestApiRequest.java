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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface RestApiRequest {
  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  <T> T getRequestBody() throws JsonProcessingException;

  void respondOk(Object response) throws JsonProcessingException;

  void respondAsync(SafeFuture<AsyncApiResponse> futureResponse);

  void respondOk(Object response, CacheLength cacheLength) throws JsonProcessingException;

  void respondError(int statusCode, String message) throws JsonProcessingException;

  void respondWithCode(int statusCode);

  <T> T getPathParameter(ParameterMetadata<T> parameterMetadata);

  <T> Optional<T> getOptionalQueryParameter(ParameterMetadata<T> parameterMetadata);

  <T> T getQueryParameter(ParameterMetadata<T> parameterMetadata);

  <T> void handleOptionalResult(
      SafeFuture<Optional<T>> future,
      RestApiRequestImpl.ResultProcessor<T> resultProcessor,
      int missingStatus);
}
