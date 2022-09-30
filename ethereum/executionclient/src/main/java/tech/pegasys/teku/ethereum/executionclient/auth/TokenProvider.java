/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TokenProvider {
  private final JwtConfig jwtConfig;

  public TokenProvider(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
  }

  public Optional<Token> token(final UInt64 instantInMillis) {
    final long expiresInMillis = TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS);
    final UInt64 expiry = instantInMillis.plus(expiresInMillis);
    final String jwtToken =
        Jwts.builder()
            .setIssuedAt(Date.from(Instant.ofEpochMilli(instantInMillis.longValue())))
            .signWith(jwtConfig.getKey(), SignatureAlgorithm.HS256)
            .compact();
    return Optional.of(new Token(jwtToken, expiry));
  }
}
