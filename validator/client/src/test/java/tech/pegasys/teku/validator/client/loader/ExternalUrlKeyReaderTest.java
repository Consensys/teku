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

  @Test
  void readKeys_validUrlReturnsValidKeys() throws IOException {
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenReturn(expectedKeys);
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    assertThat(reader.readKeys()).contains(publicKey1, publicKey2);
    verify(mapper).readValue(any(URL.class), eq(String[].class));
  }

  @Test
  void readKeys_validUrlReturnsEmptyKeys() throws IOException {
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenReturn(new String[] {});
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    assertThat(reader.readKeys()).isEmpty();
    verify(mapper).readValue(any(URL.class), eq(String[].class));
  }

  @Test
  void readKeys_validUrlReturnsInvalidKeys() throws IOException {
    when(mapper.readValue(any(URL.class), eq(String[].class)))
        .thenReturn(new String[] {"invalid", "keys"});
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    assertThatThrownBy(() -> reader.readKeys().collect(Collectors.toSet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid odd-length hex binary representation");
    verify(mapper).readValue(any(URL.class), eq(String[].class));
  }

  @Test
  void readKeys_malformedUrlString() {
    final String invalidUrl = "invalid:url";
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(invalidUrl, mapper, asyncRunner);

    assertThatThrownBy(reader::readKeys)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed to load public keys from invalid URL: " + invalidUrl)
        .hasRootCauseInstanceOf(MalformedURLException.class);
    verifyNoInteractions(mapper);
  }

  @Test
  void readKeysWithRetry_unreachableUrlRetryUntilReachable() throws IOException {
    final UnknownHostException exception = new UnknownHostException("Unknown host");
    when(mapper.readValue(any(URL.class), eq(String[].class)))
        .thenThrow(exception, exception, exception)
        .thenReturn(expectedKeys);
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    final SafeFuture<String[]> keys = reader.retry();
    for (int i = 0; i < 3; i++) {
      assertThat(keys).isNotCompleted();
      timeProvider.advanceTimeBy(DELAY);
      asyncRunner.executeQueuedActions();
    }
    assertThat(keys).isCompletedWithValue(expectedKeys);
    verify(mapper, times(4)).readValue(any(URL.class), eq(String[].class));
  }

  @Test
  void readKeysWithRetry_unreachableUrlRetryUntilMaxRetries() throws IOException {
    final IOException exception = new ConnectException("Connection refused");
    when(mapper.readValue(any(URL.class), eq(String[].class))).thenThrow(exception);
    final ExternalUrlKeyReader reader = new ExternalUrlKeyReader(VALID_URL, mapper, asyncRunner);

    final SafeFuture<String[]> keys = reader.retry();
    for (int i = 0; i < MAX_RETRIES; i++) {
      assertThat(keys).isNotCompleted();
      timeProvider.advanceTimeBy(DELAY);
      asyncRunner.executeQueuedActions();
    }
    assertThat(keys).isCompletedExceptionally();
    assertThatThrownBy(keys::join)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(InvalidConfigurationException.class)
        .hasRootCause(exception);
    verify(mapper, times(MAX_RETRIES + 1)).readValue(any(URL.class), eq(String[].class));
  }
}
