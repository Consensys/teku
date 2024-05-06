/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExternalUrlKeyReaderTest {
  private static final Duration DELAY = Duration.ofSeconds(5);
  private static final int MAX_RETRIES = 59;
  private static final String VALID_URL = "http://test:0000/api/v1/eth2/publicKeys";

  private final ObjectMapper mapper = mock(ObjectMapper.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
  private final String[] expectedKeys =
      new String[] {publicKey1.toHexString(), publicKey2.toHexString()};

  private ExternalUrlKeyReader reader;

  @Test
  void malformedUrlString() {
    final String invalidUrl = "invalid:url";
    assertThatThrownBy(() -> new ExternalUrlKeyReader(invalidUrl, mapper, Optional.empty()))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed to load public keys from invalid URL: " + invalidUrl)
        .hasRootCauseInstanceOf(MalformedURLException.class);
    verifyNoInteractions(mapper);
  }

  @Test
  void readKeys_retryDisabled_validUrlReturnsValidKeys() throws IOException {
    initialise(expectedKeys, false);

    assertThat(reader.readKeys()).contains(publicKey1, publicKey2);
    verifyReadCall();
  }

  @Test
  void readKeys_retryDisabled_validUrlReturnsEmptyKeys() throws IOException {
    initialise(new String[] {}, false);

    assertThat(reader.readKeys()).isEmpty();
    verifyReadCall();
  }

  @Test
  void readKeys_retryDisabled_validUrlReturnsInvalidKeys() throws IOException {
    initialise(new String[] {"invalid", "keys"}, false);

    assertThatThrownBy(() -> reader.readKeys().collect(Collectors.toSet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid odd-length hex binary representation");
    verifyReadCall();
  }

  @Test
  void readKeys_retryDisabled_unreachableUrlFails() throws IOException {
    final UnknownHostException exception = new UnknownHostException("Unknown host");
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenThrow(exception);
    reader = new ExternalUrlKeyReader(VALID_URL, mapper, Optional.empty());

    assertThatThrownBy(reader::readKeys)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(InvalidConfigurationException.class)
        .hasRootCause(exception);
    verifyReadCall();
  }

  @Test
  void readKeys_retryEnabled_validUrlReturnsValidKeys() throws IOException {
    initialise(expectedKeys, true);

    assertThat(reader.readKeys()).contains(publicKey1, publicKey2);
    verifyReadCall();
  }

  @Test
  void readKeys_retryEnabled_validUrlReturnsEmptyKeys() throws IOException {
    initialise(new String[] {}, true);

    assertThat(reader.readKeys()).isEmpty();
    verifyReadCall();
  }

  @Test
  void readKeys_retryEnabled_validUrlReturnsInvalidKeys() throws IOException {
    initialise(new String[] {"invalid", "keys"}, true);

    assertThatThrownBy(() -> reader.readKeys().collect(Collectors.toSet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid odd-length hex binary representation");
    verifyReadCall();
  }

  @Test
  void readKeysWithRetry_unreachableUrlRetryUntilReachable() throws IOException {
    final UnknownHostException exception = new UnknownHostException("Unknown host");
    when(mapper.readValue(any(URL.class), eq(String[].class)))
        .thenThrow(exception, exception, exception)
        .thenReturn(expectedKeys);
    reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    final SafeFuture<String[]> keys = reader.getKeysWithRetry(asyncRunner);
    for (int i = 0; i < 3; i++) {
      assertThat(keys).isNotCompleted();
      timeProvider.advanceTimeBy(DELAY);
      asyncRunner.executeQueuedActions();
    }
    assertThat(keys).isCompletedWithValue(expectedKeys);
    verifyReadCalls(4);
  }

  @Test
  void readKeysWithRetry_unreachableUrlRetryUntilMaxRetries() throws IOException {
    final IOException exception = new ConnectException("Connection refused");
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenThrow(exception);
    reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    final SafeFuture<String[]> keys = reader.getKeysWithRetry(asyncRunner);
    for (int i = 0; i < MAX_RETRIES; i++) {
      assertThat(keys).isNotCompleted();
      timeProvider.advanceTimeBy(DELAY);
      asyncRunner.executeQueuedActions();
    }
    assertThat(keys).isCompletedExceptionally();
    assertThatThrownBy(keys::join).isInstanceOf(CompletionException.class).hasRootCause(exception);
    verifyReadCalls(MAX_RETRIES + 1);
  }

  private void initialise(final String[] keys, final boolean retryEnabled) throws IOException {
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenReturn(keys);
    reader =
        new ExternalUrlKeyReader(
            VALID_URL, mapper, Optional.ofNullable(retryEnabled ? asyncRunner : null));
  }

  private void verifyReadCall() throws IOException {
    verifyReadCalls(1);
  }

  private void verifyReadCalls(final int times) throws IOException {
    verify(mapper, times(times)).readValue(any(URL.class), eq(String[].class));
  }
}
