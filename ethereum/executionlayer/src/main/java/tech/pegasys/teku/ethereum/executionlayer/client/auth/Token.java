package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import java.util.Date;

public class Token {
  private final String jwtToken;
  private final Date expiry;

  public Token(String jwtToken, Date expiry) {
    this.jwtToken = jwtToken;
    this.expiry = expiry;
  }

  public boolean isAvailableAt(Date instant) {
    return instant.before(expiry);
  }

  public String getJwtToken() {
    return jwtToken;
  }
}
