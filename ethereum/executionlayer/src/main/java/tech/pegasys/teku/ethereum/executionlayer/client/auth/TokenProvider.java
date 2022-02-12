package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

public class TokenProvider {
  public Optional<Token> token(Date instant) throws IOException {
    return Optional.empty();
  }
}
