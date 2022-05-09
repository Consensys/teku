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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jClientBuilderTest {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

  @Test
  public void shouldFailSettingBadEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    assertThatThrownBy(() -> builder.endpoint("http://^"))
        .isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoTimeout() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder.timeProvider(mock(TimeProvider.class)).endpoint("http://localhost");
    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildPreconditionCheckWithNoTimeprovider() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder.timeout(DEFAULT_TIMEOUT).endpoint("http://localhost");
    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldFailBuildWithUnsupportedEndpointScheme() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("abc://localhost");
    assertThatThrownBy(builder::build).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldBuildHttpClientWithHttpEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("http://localhost");
    Web3JClient client = builder.build();
    assertThat(client).isInstanceOf(Web3jHttpClient.class);
  }

  @Test
  public void shouldBuildHttpClientWithHttpsEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("https://localhost");
    Web3JClient client = builder.build();
    assertThat(client).isInstanceOf(Web3jHttpClient.class);
  }

  @Test
  public void shouldBuildWebsocketClientWithWsEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("ws://localhost");
    Web3JClient client = builder.build();
    assertThat(client).isInstanceOf(Web3jWebsocketClient.class);
  }

  @Test
  public void shouldBuildWebsocketClientWithWssEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("wss://localhost");
    Web3JClient client = builder.build();
    assertThat(client).isInstanceOf(Web3jWebsocketClient.class);
  }

  @Test
  public void shouldBuildIpcClientWithFileEndpoint() {
    Web3jClientBuilder builder = new Web3jClientBuilder();
    builder
        .timeProvider(mock(TimeProvider.class))
        .timeout(DEFAULT_TIMEOUT)
        .endpoint("file:/some/path");
    Web3JClient client = builder.build();
    assertThat(client).isInstanceOf(Web3jIpcClient.class);
  }
}
