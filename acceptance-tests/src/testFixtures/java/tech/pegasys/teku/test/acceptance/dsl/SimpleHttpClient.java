/*
 * Copyright 2019 ConsenSys AG.
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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SimpleHttpClient {
  private final OkHttpClient httpClient = new OkHttpClient();
  private static final okhttp3.MediaType JSON =
      okhttp3.MediaType.parse("application/json; charset=utf-8");

  public String get(final URI baseUrl, final String path) throws IOException {
    final URL url = baseUrl.resolve(path).toURL();
    final Response response =
        httpClient.newCall(new Request.Builder().url(url).get().build()).execute();
    assertThat(response.isSuccessful())
        .describedAs(
            "Received unsuccessful response from %s: %s %s",
            url, response.code(), response.message())
        .isTrue();
    final ResponseBody body = response.body();
    assertThat(body).isNotNull();
    return body.string();
  }

  public String post(final URI baseUrl, final String path, final String jsonBody)
      throws IOException {
    final RequestBody requestBody = RequestBody.create(jsonBody, JSON);
    final Response response =
        httpClient
            .newCall(
                new Request.Builder().url(baseUrl.resolve(path).toURL()).post(requestBody).build())
            .execute();
    assertThat(response.isSuccessful()).isTrue();
    final ResponseBody responseBody = response.body();
    assertThat(responseBody).isNotNull();
    return responseBody.string();
  }

  public String delete(final URI baseUrl, final String path, final String jsonBody)
      throws IOException {
    final RequestBody requestBody = RequestBody.create(jsonBody, JSON);
    final Response response =
        httpClient
            .newCall(
                new Request.Builder()
                    .url(baseUrl.resolve(path).toURL())
                    .delete(requestBody)
                    .build())
            .execute();
    assertThat(response.isSuccessful()).isTrue();
    final ResponseBody responseBody = response.body();
    assertThat(responseBody).isNotNull();
    return responseBody.string();
  }
}
