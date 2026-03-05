/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.executionclient.sszrest;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Low-level HTTP client for SSZ-REST Engine API transport. Sends POST requests with
 * application/octet-stream body (SSZ-encoded) and reads SSZ-encoded responses.
 *
 * <p>JWT authentication is handled by the OkHttp interceptor already configured on the provided
 * OkHttpClient instance.
 */
public class SszRestClient {

  private static final Logger LOG = LogManager.getLogger();

  static final MediaType SSZ_MEDIA_TYPE = MediaType.parse("application/octet-stream");

  private final OkHttpClient httpClient;
  private final String baseUrl;

  public SszRestClient(final OkHttpClient httpClient, final String baseUrl) {
    this.httpClient = httpClient;
    // Strip trailing slash for consistent URL building
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  /**
   * Sends a POST request with SSZ-encoded body and returns the SSZ-encoded response bytes.
   *
   * @param path the API path (e.g. "/eth/v1/engine/new_payload")
   * @param body the SSZ-encoded request body
   * @return a SafeFuture resolving to the SSZ-encoded response bytes
   */
  public SafeFuture<byte[]> doRequest(final String path, final byte[] body) {
    final Request request =
        new Request.Builder()
            .url(baseUrl + path)
            .post(RequestBody.create(body, SSZ_MEDIA_TYPE))
            .header("Accept", "application/octet-stream")
            .build();

    return SafeFuture.of(
        () -> {
          LOG.trace("SSZ-REST request: {} {}", request.method(), request.url());
          try (Response response = httpClient.newCall(request).execute()) {
            final byte[] responseBody =
                response.body() != null ? response.body().bytes() : new byte[0];
            if (response.isSuccessful()) {
              LOG.trace(
                  "SSZ-REST response: {} {} -> {} ({} bytes)",
                  request.method(),
                  request.url(),
                  response.code(),
                  responseBody.length);
              return responseBody;
            }
            throw SszRestException.fromJsonError(responseBody, response.code());
          } catch (final IOException e) {
            throw SszRestException.fromNetworkError(e);
          }
        });
  }

  public String getBaseUrl() {
    return baseUrl;
  }
}
