/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuthLoggingIntercepter;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

class RemoteSpecLoader {
  private static BiConsumer<Throwable, Integer> retryErrObserver =
      (Throwable e, Integer attempt) -> {
        SubCommandLogger.SUB_COMMAND_LOG.error(e.getMessage());
      };

  private static final long RETRY_DELAY = 5000;

  static Spec getSpec(OkHttpValidatorRestApiClient apiClient) {
    try {
      return apiClient
          .getConfigSpec()
          .map(response -> SpecConfigLoader.loadConfig(response.data))
          .map(SpecFactory::create)
          .orElseThrow();
    } catch (Exception e) {
      String errMsg =
          String.format(
              "Failed to retrieve network spec from beacon node endpoint '%s'.\nDetails: %s",
              apiClient.getBaseEndpoint(), e.getMessage());
      throw new InvalidConfigurationException(errMsg, e);
    }
  }

  static Spec getSpec(URI beaconEndpoint) {
    return getSpec(createApiClient(beaconEndpoint));
  }

  static Spec getSpecWithRetry(URI beaconEndpoint, int maxAttempts) throws Throwable {
    return retry(() -> getSpec(beaconEndpoint), retryErrObserver, maxAttempts);
  }

  static Spec getSpecWithRetry(OkHttpValidatorRestApiClient apiClient, int maxAttempts)
      throws Throwable {
    return retry(() -> getSpec(apiClient), retryErrObserver, maxAttempts);
  }

  private static <T> T retry(
      Callable<T> f, BiConsumer<Throwable, Integer> errObserver, int maxAttempts) throws Throwable {
    return retry(f, errObserver, maxAttempts, 1);
  }

  private static <T> T retry(
      Callable<T> f, BiConsumer<Throwable, Integer> errObserver, int maxAttempts, int attempt)
      throws Throwable {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException(
          "The number of retry attempts for an operation has to be greater than 1");
    }
    try {
      return f.call();
    } catch (Throwable e) {
      errObserver.accept(e, attempt);
      if (attempt >= maxAttempts) throw e;
      delayFor(attempt, errObserver);
      return retry(f, errObserver, maxAttempts, ++attempt);
    }
  }

  private static void delayFor(int attempt, BiConsumer<Throwable, Integer> errObserver) {
    try {
      Thread.sleep(RETRY_DELAY);
    } catch (InterruptedException e) {
      errObserver.accept(e, attempt);
      throw new RuntimeException(e);
    }
  }

  static OkHttpValidatorRestApiClient createApiClient(final URI baseEndpoint) {
    HttpUrl apiEndpoint = HttpUrl.get(baseEndpoint);
    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS);
    OkHttpClientAuthLoggingIntercepter.addAuthenticator(apiEndpoint, httpClientBuilder);
    // Strip any authentication info from the URL to ensure it doesn't get logged.
    apiEndpoint = apiEndpoint.newBuilder().username("").password("").build();
    final OkHttpClient okHttpClient = httpClientBuilder.build();
    return new OkHttpValidatorRestApiClient(apiEndpoint, okHttpClient);
  }
}
