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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class SimpleHttpClient {
  private static final Logger LOG = LogManager.getLogger();
  protected OkHttpClient httpClient;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  public SimpleHttpClient() {
    httpClient = new OkHttpClient();
  }

  public String get(final URI baseUrl, final String path) throws IOException {
    return this.get(baseUrl, path, Collections.emptyMap());
  }

  public String get(final URI baseUrl, final String path, Map<String, String> headers)
      throws IOException {
    final ResponseBody body = getResponseBody(baseUrl, path, headers);
    return body.string();
  }

  public Bytes getAsBytes(final URI baseUrl, final String path, Map<String, String> headers)
      throws IOException {
    final ResponseBody body = getResponseBody(baseUrl, path, headers);
    return Bytes.wrap(body.bytes());
  }

  private ResponseBody getResponseBody(
      final URI baseUrl, final String path, final Map<String, String> headers) throws IOException {
    LOG.debug("GET {}, headers: {}", path, headers.toString());
    final URL url = baseUrl.resolve(path).toURL();
    final Request.Builder builder = new Request.Builder().url(baseUrl.resolve(path).toURL()).get();
    headers.forEach(builder::header);
    final Response response = httpClient.newCall(builder.build()).execute();
    final ResponseBody body = response.body();
    if (!response.isSuccessful()) {
      fail(
          "Received unsuccessful response from %s: %s %s %s",
          url, response.code(), response.message(), body != null ? body.string() : "null");
    }
    assertThat(body).isNotNull();
    return body;
  }

  public String post(final URI baseUrl, final String path, final String jsonBody)
      throws IOException {
    return this.post(baseUrl, path, jsonBody, Collections.emptyMap());
  }

  public String post(
      final URI baseUrl, final String path, final String jsonBody, Map<String, String> headers)
      throws IOException {
    final RequestBody requestBody = RequestBody.create(jsonBody, JSON);
    final Request.Builder builder =
        new Request.Builder().url(baseUrl.resolve(path).toURL()).post(requestBody);
    headers.forEach(builder::header);
    LOG.debug("POST {}{}, body {}", baseUrl, path, jsonBody);

    final Response response = httpClient.newCall(builder.build()).execute();
    final ResponseBody responseBody = response.body();
    if (!response.isSuccessful()) {
      LOG.debug("POST RESPONSE CODE: {}, BODY: {}", response.code(), responseBody.string());
    }
    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseBody).isNotNull();
    return responseBody.string();
  }

  public String delete(
      final URI baseUrl, final String path, final String jsonBody, Map<String, String> headers)
      throws IOException {
    final RequestBody requestBody = RequestBody.create(jsonBody, JSON);

    final Request.Builder builder =
        new Request.Builder().url(baseUrl.resolve(path).toURL()).delete(requestBody);
    headers.forEach(builder::header);
    LOG.debug("DELETE {}{}, body {}", baseUrl, path, jsonBody);

    final Response response = httpClient.newCall(builder.build()).execute();
    assertThat(response.isSuccessful()).isTrue();
    final ResponseBody responseBody = response.body();
    assertThat(responseBody).isNotNull();
    return responseBody.string();
  }
}
