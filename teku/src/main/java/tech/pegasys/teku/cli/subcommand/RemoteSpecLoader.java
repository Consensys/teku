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

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuthLoggingIntercepter;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

class RemoteSpecLoader {
  static Spec getSpec(OkHttpValidatorRestApiClient apiClient) {
    return apiClient
        .getConfigSpec()
        .map(response -> SpecConfigLoader.loadConfig(response.data))
        .map(SpecFactory::create)
        .orElseThrow();
  }

  static Spec getSpecOrExit(URI beaconEndpoint) {
    return getSpecOrExit(createApiClient(beaconEndpoint));
  }

  static Spec getSpecOrExit(OkHttpValidatorRestApiClient apiClient) {
    try {
      return getSpec(apiClient);
    } catch (Exception ex) {
      SUB_COMMAND_LOG.error(
          "Failed to retrieve network config. Check beacon node is accepting REST requests.", ex);
      System.exit(1);
    }
    return null;
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
