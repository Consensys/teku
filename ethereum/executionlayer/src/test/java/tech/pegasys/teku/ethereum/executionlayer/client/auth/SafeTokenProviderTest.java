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

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SafeTokenProviderTest {
  private Key jwtSecretKey;

  private SafeTokenProvider safeTokenProvider;

  @BeforeEach
  void setUp() {
    jwtSecretKey = TestHelper.generateJwtSecret();
    safeTokenProvider = new SafeTokenProvider(new TokenProvider(new JwtConfig(jwtSecretKey)));
  }

  @Test
  void testGetToken_TokenUpdateOnExpiry() {
    UInt64 timeInMillis = UInt64.valueOf(System.currentTimeMillis());
    final Optional<Token> originalToken = safeTokenProvider.token(timeInMillis);
    assertThat(validateTokenAtInstant(originalToken, timeInMillis)).isTrue();
    assertThat(
            validateJwtTokenAtInstant(
                jwtSecretKey, originalToken.get().getJwtToken(), timeInMillis))
        .isTrue();

    timeInMillis = timeInMillis.plus(TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS));
    final Optional<Token> updatedToken = safeTokenProvider.token(timeInMillis);
    assertThat(validateTokenAtInstant(updatedToken, timeInMillis)).isTrue();
    assertThat(
            validateJwtTokenAtInstant(jwtSecretKey, updatedToken.get().getJwtToken(), timeInMillis))
        .isTrue();

    assertThat(originalToken.equals(updatedToken)).isFalse();
  }

  @Test
  void testGetToken_TokenPreExpiry() {
    UInt64 timeInMillis = UInt64.valueOf(System.currentTimeMillis());
    final Optional<Token> originalToken = safeTokenProvider.token(timeInMillis);
    assertThat(validateTokenAtInstant(originalToken, timeInMillis)).isTrue();
    assertThat(
            validateJwtTokenAtInstant(
                jwtSecretKey, originalToken.get().getJwtToken(), timeInMillis))
        .isTrue();

    timeInMillis = timeInMillis.plus(TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS - 1));
    final Optional<Token> updatedToken = safeTokenProvider.token(timeInMillis);
    assertThat(validateTokenAtInstant(updatedToken, timeInMillis)).isTrue();
    assertThat(
            validateJwtTokenAtInstant(jwtSecretKey, updatedToken.get().getJwtToken(), timeInMillis))
        .isTrue();

    assertThat(originalToken.equals(updatedToken)).isTrue();
  }

  public static boolean validateTokenAtInstant(
      final Optional<Token> optionalToken, final UInt64 instantInMillis) {
    return optionalToken.isPresent()
        && (optionalToken.get().isAvailableAt(instantInMillis)
            && StringUtils.isNotBlank(optionalToken.get().getJwtToken()));
  }

  public static boolean validateJwtTokenAtInstant(
      final Key jwtSecretKey, final String jwtToken, final UInt64 instantInMillis) {
    final long issuedAtInMillis =
        Jwts.parser()
            .setSigningKey(jwtSecretKey)
            .parseClaimsJws(jwtToken)
            .getBody()
            .get(Claims.ISSUED_AT, Long.class);
    return instantInMillis.isLessThan(TimeUnit.SECONDS.toMillis(issuedAtInMillis));
  }
}
