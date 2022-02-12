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

package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.Response;

public class JwtAuthInterceptor implements Interceptor {
  private final SafeTokenProvider tokenProvider;

  public JwtAuthInterceptor(final JwtConfig jwtConfig) {
    this.tokenProvider = new SafeTokenProvider(new TokenProvider(jwtConfig));
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    Optional<Token> optionalToken = tokenProvider.token(new Date());
    if (optionalToken.isEmpty()) {
      return chain.proceed(chain.request());
    }
    final Token token = optionalToken.get();
    final String authHeader = String.format("%s", token.getJwtToken());
    return chain.proceed(chain.request().newBuilder().header("Authorization", authHeader).build());
  }
}
