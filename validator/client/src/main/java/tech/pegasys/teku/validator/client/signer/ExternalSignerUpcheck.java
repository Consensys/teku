/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.signer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExternalSignerUpcheck {
  private final URL signingServiceUrl;
  private final Duration timeout;
  private final HttpClient httpClient;

  public static final String UPCHECK_ENDPOINT = "/upcheck";

  public ExternalSignerUpcheck(
      final HttpClient httpClient, final URL signingServiceUrl, final Duration timeout) {
    this.httpClient = httpClient;
    this.signingServiceUrl = signingServiceUrl;
    this.timeout = timeout;
  }

  public boolean upcheck() {
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(signingServiceUrl.toURI().resolve(UPCHECK_ENDPOINT))
              .timeout(timeout)
              .GET()
              .build();
      final HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (final URISyntaxException | IOException | InterruptedException e) {
      return false;
    }
  }
}
