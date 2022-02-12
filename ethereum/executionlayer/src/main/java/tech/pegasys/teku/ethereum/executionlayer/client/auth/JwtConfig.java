package tech.pegasys.teku.ethereum.executionlayer.client.auth;

public class JwtConfig {
  private String hexEncodedSecretKey;

  private final long expiresInSeconds = 5;

  public JwtConfig(final String hexEncodedSecretKey) {
    this.hexEncodedSecretKey = hexEncodedSecretKey;
  }

  public String getHexEncodedSecretKey() {
    return hexEncodedSecretKey;
  }

  public void setHexEncodedSecretKey(String hexEncodedSecretKey) {
    this.hexEncodedSecretKey = hexEncodedSecretKey;
  }

  public long getExpiresInSeconds() {
    return expiresInSeconds;
  }
}
