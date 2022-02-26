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
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jClientBuilder {
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

  public Web3JClient build() {
    checkNotNull(timeProvider);
    checkNotNull(endpoint);
    switch (endpoint.getScheme()) {
      case "http":
      case "https":
        return new Web3jHttpClient(endpoint, timeProvider, jwtConfigOpt);
      case "ws":
      case "wss":
        return new Web3jWebsocketClient(endpoint, timeProvider, jwtConfigOpt);
      case "file":
        return new Web3jIpcClient(endpoint, timeProvider, jwtConfigOpt);
      default:
        throw new InvalidConfigurationException(
            String.format(
                "Endpoint \"%s\" scheme is not supported. Use "
                    + "http://, https://, ws://, wss:// or file: for IPC file path",
                endpoint));
    }
  }
}
