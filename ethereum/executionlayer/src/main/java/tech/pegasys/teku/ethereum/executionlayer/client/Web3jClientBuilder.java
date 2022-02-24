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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtAuthHttpInterceptor;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jClientBuilder {
  private static final Logger LOG = LogManager.getLogger();
  private TimeProvider timeProvider;
  private URI endpoint;
  private Optional<JwtConfig> jwtConfigOpt = Optional.empty();

  public Web3jClientBuilder endpoint(String endpoint) {
    this.endpoint = parseEndpoint(endpoint);
    return this;
  }

  private URI parseEndpoint(final String endpoint) {
    final URI endpointUri;
    try {
      endpointUri = new URI(endpoint);
    } catch (URISyntaxException ex) {
      throw new InvalidConfigurationException(
          String.format("%s is not a correct endpoint URI", endpoint), ex);
    }
    return endpointUri;
  }

  public Web3jClientBuilder jwtConfigOpt(Optional<JwtConfig> jwtConfig) {
    this.jwtConfigOpt = jwtConfig;
    return this;
  }

  public Web3jClientBuilder timeProvider(TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  private OkHttpClient createOkHttpClient() {
    final OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (LOG.isTraceEnabled()) {
      HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    jwtConfigOpt.ifPresent(
        config -> builder.addInterceptor(new JwtAuthHttpInterceptor(config, timeProvider)));
    return builder.build();
  }

  public Web3JClient build() {
    checkNotNull(timeProvider);
    checkNotNull(endpoint);
    switch (endpoint.getScheme()) {
      case "http":
        final OkHttpClient okHttpClient = createOkHttpClient();
        Web3jService httpService = new HttpService(endpoint.toString(), okHttpClient);
        return new Web3jHttpClient(timeProvider, httpService);
      case "ws":
        WebSocketClient webSocketClient = new WebSocketClient(endpoint);
        WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
        return new Web3jWebsocketClient(
            timeProvider, webSocketClient, webSocketService, jwtConfigOpt);
      default:
        throw new InvalidConfigurationException(
            String.format(
                "Endpoint \"%s\" scheme is not supported. http://, ws:// are supported", endpoint));
    }
  }
}
