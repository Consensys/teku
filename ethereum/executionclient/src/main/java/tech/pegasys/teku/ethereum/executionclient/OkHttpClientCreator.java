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

import java.time.Duration;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtAuthHttpInterceptor;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class OkHttpClientCreator {

  public static OkHttpClient create(
      final Optional<JwtConfig> jwtConfig,
      final Duration timeout,
      final Optional<HttpLoggingInterceptor> loggingInterceptor,
      final TimeProvider timeProvider) {
    final OkHttpClient.Builder builder =
        new OkHttpClient.Builder().readTimeout(timeout).writeTimeout(timeout);
    loggingInterceptor.ifPresent(builder::addInterceptor);
    jwtConfig.ifPresent(
        config -> builder.addInterceptor(new JwtAuthHttpInterceptor(config, timeProvider)));
    return builder.build();
  }
}
