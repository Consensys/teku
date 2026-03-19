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

package tech.pegasys.teku.ethereum.executionclient;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class OkHttpExecutionEngineClientFactory {

  public static ExecutionEngineClient create(
      final OkHttpClient httpClient,
      final Supplier<AsyncRunner> asyncRunnerSupplier,
      final String endpoint,
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher,
      final Collection<String> nonCriticalMethods) {
    final URI uri = URI.create(endpoint);
    final String scheme = uri.getScheme();
    if (scheme == null) {
      throw invalidConfigurationException(endpoint);
    }
    return switch (scheme) {
      case "http", "https" ->
          new OkHttpHttpExecutionEngineClient(
              httpClient,
              endpoint,
              eventLog,
              timeProvider,
              executionClientEventsPublisher,
              nonCriticalMethods);
      case "ws", "wss" ->
          new OkHttpWebSocketExecutionEngineClient(
              httpClient,
              endpoint,
              eventLog,
              timeProvider,
              executionClientEventsPublisher,
              nonCriticalMethods);
      case "file" ->
          new IpcSocketExecutionEngineClient(
              asyncRunnerSupplier.get(),
              Path.of(uri.getPath()),
              eventLog,
              timeProvider,
              executionClientEventsPublisher,
              nonCriticalMethods);
      default -> throw invalidConfigurationException(endpoint);
    };
  }

  private static InvalidConfigurationException invalidConfigurationException(
      final String endpoint) {
    return new InvalidConfigurationException(
        String.format(
            "Endpoint \"%s\" scheme is not supported. Use http://, https://, ws://, wss://, or file://",
            endpoint));
  }
}
