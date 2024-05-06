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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class ExternalUrlKeyReader {
  private static final Duration FIXED_DELAY = Duration.ofSeconds(5);
  private static final int MAX_RETRIES = 59;

  private final URL url;
  private final ObjectMapper mapper;
  private final Optional<AsyncRunner> asyncRunner;

  @VisibleForTesting
  public ExternalUrlKeyReader(
      final String urlString, final ObjectMapper mapper, final AsyncRunner asyncRunner) {
    this(urlString, mapper, Optional.of(asyncRunner));
  }

  public ExternalUrlKeyReader(
      final String urlString, final ObjectMapper mapper, final Optional<AsyncRunner> asyncRunner) {
    this.url = getUrl(urlString);
    this.mapper = mapper;
    this.asyncRunner = asyncRunner;
  }

  public Stream<BLSPublicKey> readKeys() {
    final String[] keys =
        asyncRunner
            .map(this::getKeysWithRetry)
            .orElseGet(this::readUrl)
            .exceptionallyCompose(
                ex ->
                    SafeFuture.failedFuture(
                        new InvalidConfigurationException(
                            "Failed to load public keys from URL: " + url, ex)))
            .join();
    return Arrays.stream(keys).map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
  }

  @VisibleForTesting
  SafeFuture<String[]> getKeysWithRetry(final AsyncRunner asyncRunner) {
    return asyncRunner.runWithRetry(this::readUrl, FIXED_DELAY, MAX_RETRIES);
  }

  private SafeFuture<String[]> readUrl() {
    try {
      return SafeFuture.completedFuture(mapper.readValue(url, String[].class));
    } catch (IOException e) {
      STATUS_LOG.failedToLoadPublicKeysFromUrl(url.toString());
      return SafeFuture.failedFuture(e);
    }
  }

  private URL getUrl(final String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Failed to load public keys from invalid URL: " + url, e);
    }
  }
}
