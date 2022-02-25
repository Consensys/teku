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

package tech.pegasys.teku.ethereum.executionlayer.client;

import java.net.URI;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtAuthHttpInterceptor;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jHttpClient extends Web3JClient {
  private static final Logger LOG = LogManager.getLogger();

  public Web3jHttpClient(
      final URI endpoint, final TimeProvider timeProvider, final Optional<JwtConfig> jwtConfig) {
    super(timeProvider);
    final OkHttpClient okHttpClient = createOkHttpClient(jwtConfig, timeProvider);
    Web3jService httpService = new HttpService(endpoint.toString(), okHttpClient);
    initWeb3jService(httpService);
  }

  private OkHttpClient createOkHttpClient(
      final Optional<JwtConfig> jwtConfig, final TimeProvider timeProvider) {
    final OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (LOG.isTraceEnabled()) {
      HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    jwtConfig.ifPresent(
        config -> builder.addInterceptor(new JwtAuthHttpInterceptor(config, timeProvider)));
    return builder.build();
  }
}
