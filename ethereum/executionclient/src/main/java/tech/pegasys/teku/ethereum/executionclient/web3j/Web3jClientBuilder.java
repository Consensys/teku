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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jClientBuilder {
  private TimeProvider timeProvider;
  private URI endpoint;
  private Optional<JwtConfig> jwtConfigOpt = Optional.empty();
  private Duration timeout;
  private ExecutionClientEventsChannel executionClientEventsPublisher;
  private final Collection<String> nonCriticalMethods = new HashSet<>();

  public Web3jClientBuilder endpoint(String endpoint) {
    this.endpoint = parseEndpoint(endpoint);
    return this;
  }

  public Web3jClientBuilder timeout(Duration timeout) {
    this.timeout = timeout;
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

  public Web3jClientBuilder executionClientEventsPublisher(
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    this.executionClientEventsPublisher = executionClientEventsPublisher;
    return this;
  }

  public Web3jClientBuilder nonCriticalMethods(String... methods) {
    nonCriticalMethods.addAll(Arrays.asList(methods));
    return this;
  }

  public Web3JClient build() {
    checkNotNull(timeProvider);
    checkNotNull(executionClientEventsPublisher);
    checkNotNull(endpoint);
    checkNotNull(timeout);
    requireNonNull(endpoint.getScheme(), () -> prepareInvalidSchemeMessage(endpoint));
    switch (endpoint.getScheme()) {
      case "http":
      case "https":
        return new Web3jHttpClient(
            EVENT_LOG,
            endpoint,
            timeProvider,
            timeout,
            jwtConfigOpt,
            executionClientEventsPublisher,
            nonCriticalMethods);
      case "ws":
      case "wss":
        return new Web3jWebsocketClient(
            EVENT_LOG,
            endpoint,
            timeProvider,
            jwtConfigOpt,
            executionClientEventsPublisher,
            nonCriticalMethods);
      case "file":
        return new Web3jIpcClient(
            EVENT_LOG,
            endpoint,
            timeProvider,
            jwtConfigOpt,
            executionClientEventsPublisher,
            nonCriticalMethods);
      default:
        throw new InvalidConfigurationException(prepareInvalidSchemeMessage(endpoint));
    }
  }

  private String prepareInvalidSchemeMessage(final URI endpoint) {
    return String.format(
        "Endpoint \"%s\" scheme is not supported. Use "
            + "http://, https://, ws://, wss:// or file: for IPC file path",
        endpoint);
  }
}
