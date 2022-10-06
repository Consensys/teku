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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jClientBuilderTest {

  private final String endpoint = "http://localhost";
  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private final Duration timeout = Duration.ofMinutes(1);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  @Test
  public void shouldFailSettingBadEndpoint() {
    assertThatThrownBy(() -> new Web3jClientBuilder().endpoint("http://^"))
        .isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(timeProvider)
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoTimeout() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(timeProvider)
            .endpoint(endpoint)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoTimeprovider() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .endpoint(endpoint)
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoExecutionClientEventPublisher() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder().timeProvider(timeProvider).endpoint(endpoint).timeout(timeout);

    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildWithUnsupportedEndpointScheme() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(timeProvider)
            .endpoint("abc://localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    assertThatThrownBy(builder::build).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFailBuildWithNullEndpointScheme() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(timeProvider)
            .endpoint("localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    assertThatThrownBy(builder::build).hasMessageContaining("scheme is not supported");
  }

  @Test
  public void shouldBuildHttpClientWithHttpEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(mock(TimeProvider.class))
            .endpoint("http://localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    Web3JClient client = builder.build();

    assertThat(client).isInstanceOf(Web3jHttpClient.class);
  }

  @Test
  public void shouldBuildHttpClientWithHttpsEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(mock(TimeProvider.class))
            .endpoint("https://localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    Web3JClient client = builder.build();

    assertThat(client).isInstanceOf(Web3jHttpClient.class);
  }

  @Test
  public void shouldBuildWebsocketClientWithWsEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(mock(TimeProvider.class))
            .endpoint("ws://localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    Web3JClient client = builder.build();

    assertThat(client).isInstanceOf(Web3jWebsocketClient.class);
  }

  @Test
  public void shouldBuildWebsocketClientWithWssEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(mock(TimeProvider.class))
            .endpoint("wss://localhost")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    Web3JClient client = builder.build();

    assertThat(client).isInstanceOf(Web3jWebsocketClient.class);
  }

  @Test
  public void shouldBuildIpcClientWithFileEndpoint() {
    Web3jClientBuilder builder =
        new Web3jClientBuilder()
            .timeProvider(mock(TimeProvider.class))
            .endpoint("file:/some/path")
            .timeout(timeout)
            .executionClientEventsPublisher(executionClientEventsPublisher);

    Web3JClient client = builder.build();

    assertThat(client).isInstanceOf(Web3jIpcClient.class);
  }
}
