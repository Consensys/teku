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

package tech.pegasys.teku.data.publisher;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MetricsPublisher {

  private static final Logger LOG = LogManager.getLogger();
  private final OkHttpClient client;
  private final MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

  public MetricsPublisher(final OkHttpClient client) {
    this.client = client;
  }

  public int publishMetrics(final String endpointAddress, final String json) throws IOException {
    HttpUrl endpoint = HttpUrl.get(endpointAddress);
    RequestBody body = RequestBody.create(json, mediaType);
    Request request = new Request.Builder().url(endpoint).post(body).build();
    Response response = this.client.newCall(request).execute();
    int responseCode = response.code();
    response.close();
    if (responseCode != 200) {
      LOG.error(
          "Failed to publish metrics to external metrics service. Response code {}", responseCode);
    }
    return responseCode;
  }
}
