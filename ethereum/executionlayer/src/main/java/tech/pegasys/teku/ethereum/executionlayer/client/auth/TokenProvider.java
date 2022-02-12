package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

public class TokenProvider {
  final JwtConfig jwtConfig;

  public TokenProvider(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
  }

  public Optional<Token> token(final Date instant) {
    final long expiresInMilliseconds = jwtConfig.getExpiresInSeconds() * 1000;
    final Date expiry = new Date(instant.getTime() + expiresInMilliseconds);
    final String jwtToken =
        Jwts.builder()
            .setIssuedAt(instant)
            .setExpiration(expiry)
            .signWith(SignatureAlgorithm.HS256, hexToBase64(jwtConfig.getHexEncodedSecretKey()))
            .compact();
    return Optional.of(new Token(jwtToken, expiry));
  }

  public static String hexToBase64(final String hex) {
    return Base64.getEncoder().encodeToString(new BigInteger(hex, 16).toByteArray());
  }
}
