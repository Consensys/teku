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
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class ExternalUrlKeyReader {
  private static final Duration FIXED_DELAY = Duration.ofSeconds(5);
  private static final int MAX_RETRIES = 59;

  private final String url;
  private final ObjectMapper mapper;
  private final AsyncRunner asyncRunner;

  public ExternalUrlKeyReader(
      final String url, final ObjectMapper mapper, final AsyncRunner asyncRunner) {
    this.url = url;
    this.mapper = mapper;
    this.asyncRunner = asyncRunner;
  }

  public Stream<BLSPublicKey> readKeys() {
    final String[] keys =
        readUrl()
            .exceptionallyCompose(
                ex -> {
                  if (ex instanceof MalformedURLException) {
                    return SafeFuture.failedFuture(
                        new InvalidConfigurationException(
                            "Failed to load public keys from invalid URL: " + url, ex));
                  }
                  return retry();
                })
            .join();
    return Arrays.stream(keys).map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
  }

  private SafeFuture<String[]> readUrl() {
    try {
      return SafeFuture.completedFuture(mapper.readValue(new URL(url), String[].class));
    } catch (IOException e) {
      return SafeFuture.failedFuture(e);
    }
  }

  @VisibleForTesting
  SafeFuture<String[]> retry() {
    return asyncRunner
        .runWithRetry(
            () -> {
              STATUS_LOG.failedToLoadPublicKeysFromUrl(url);
              return readUrl();
            },
            FIXED_DELAY,
            MAX_RETRIES)
        .exceptionallyCompose(
            ex ->
                SafeFuture.failedFuture(
                    new InvalidConfigurationException(
                        "Failed to load public keys from URL: " + url, ex)));
  }
}
