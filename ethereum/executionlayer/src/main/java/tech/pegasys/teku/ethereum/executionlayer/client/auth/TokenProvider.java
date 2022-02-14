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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TokenProvider {
  private final JwtConfig jwtConfig;

  public TokenProvider(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
  }

  public Optional<Token> token(final Date instant) {
    final long expiresInMilliseconds = TimeUnit.SECONDS.toMillis(jwtConfig.getExpiresInSeconds());
    final Date expiry = new Date(instant.getTime() + expiresInMilliseconds);
    final String jwtToken =
        Jwts.builder()
            .setIssuedAt(instant)
            .signWith(SignatureAlgorithm.HS256, jwtConfig.getKey())
            .compact();
    return Optional.of(new Token(jwtToken, expiry));
  }
}
