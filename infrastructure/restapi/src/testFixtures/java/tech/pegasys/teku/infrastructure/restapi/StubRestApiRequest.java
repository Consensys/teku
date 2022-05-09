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

package tech.pegasys.teku.infrastructure.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class StubRestApiRequest implements RestApiRequest {
  private static final Logger LOG = LogManager.getLogger();
  private static final int CODE_NOT_SET = -1;
  private Object requestBody = null;
  private int responseCode = CODE_NOT_SET;
  private Object responseObject = null;
  private CacheLength cacheLength = null;
  private final Map<String, String> pathParameters = new HashMap<>();
  private final Map<String, String> queryParameters = new HashMap<>();
  private final Map<String, String> optionalQueryParameters = new HashMap<>();

  public boolean responseCodeSet() {
    return responseCode != CODE_NOT_SET;
  }

  public int getResponseCode() {
    return responseCode;
  }

  public CacheLength getCacheLength() {
    return cacheLength;
  }

  public <T> void setRequestBody(T requestBody) {
    assertThat(this.requestBody).isNull();
    this.requestBody = requestBody;
  }

  @Override
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() {
    return (T) requestBody;
  }

  @Override
  public void respondOk(final Object response) throws JsonProcessingException {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    assertThat(this.responseObject).isNull();
    this.responseCode = SC_OK;
    this.responseObject = response;
  }

  @Override
  public void respondAsync(final SafeFuture<AsyncApiResponse> futureResponse) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    final AsyncApiResponse response = futureResponse.join();
    responseCode = response.getResponseCode();
    if (response.getResponseBody().isEmpty()) {
      LOG.warn("Response body was empty on async response");
    }
    responseObject = response.getResponseBody().orElse(null);
  }

  @Override
  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    assertThat(this.cacheLength).isNull();
    this.cacheLength = cacheLength;
    respondOk(response);
  }

  @Override
  public void respondError(final int statusCode, final String message) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    assertThat(this.responseObject).isNull();
    responseObject = new HttpErrorResponse(statusCode, message);
    this.responseCode = statusCode;
  }

  @Override
  public void respondWithCode(final int statusCode) {
    assertThat(this.responseCode).isEqualTo(CODE_NOT_SET);
    this.responseCode = statusCode;
  }

  public void setPathParameter(final String parameter, final String value) {
    assertThat(this.pathParameters.containsKey(parameter)).isFalse();
    this.pathParameters.put(parameter, value);
  }

  public void setQueryParameter(final String parameter, final String value) {
    assertThat(this.queryParameters.containsKey(parameter)).isFalse();
    this.queryParameters.put(parameter, value);
  }

  private void setOptionalQueryParameter(final String parameter, final String value) {
    assertThat(this.optionalQueryParameters.containsKey(parameter)).isFalse();
    this.optionalQueryParameters.put(parameter, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getPathParameter(final ParameterMetadata<T> parameterMetadata) {
    assertThat(this.pathParameters.containsKey(parameterMetadata.getName())).isTrue();
    final Object param = pathParameters.get(parameterMetadata.getName());
    return (T) param;
  }

  @Override
  public <T> Optional<T> getOptionalQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    final Optional<String> param =
        Optional.ofNullable(optionalQueryParameters.get(parameterMetadata.getName()));
    return param.map(p -> parameterMetadata.getType().deserializeFromString(p));
  }

  @Override
  public <T> T getQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    assertThat(this.queryParameters.containsKey(parameterMetadata.getName())).isTrue();
    final String param = queryParameters.get(parameterMetadata.getName());
    return parameterMetadata.getType().deserializeFromString(param);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Object getResponseBody() {
    return responseObject;
  }

  public static class Builder {
    private final Map<String, String> pathParameters = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();
    private final Map<String, String> optionalQueryParameters = new HashMap<>();

    Builder() {}

    public Builder pathParameter(final String param, final String value) {
      assertThat(pathParameters.containsKey(param)).isFalse();
      this.pathParameters.put(param, value);
      return this;
    }

    public Builder queryParameter(final String param, final String value) {
      assertThat(queryParameters.containsKey(param)).isFalse();
      this.queryParameters.put(param, value);
      return this;
    }

    public Builder optionalQueryParameter(final String param, final String value) {
      assertThat(optionalQueryParameters.containsKey(param)).isFalse();
      this.optionalQueryParameters.put(param, value);
      return this;
    }

    public StubRestApiRequest build() {
      final StubRestApiRequest request = new StubRestApiRequest();
      for (String k : pathParameters.keySet()) {
        request.setPathParameter(k, pathParameters.get(k));
      }
      for (String k : queryParameters.keySet()) {
        request.setQueryParameter(k, queryParameters.get(k));
      }
      for (String k : optionalQueryParameters.keySet()) {
        request.setOptionalQueryParameter(k, optionalQueryParameters.get(k));
      }

      return request;
    }
  }
}
