package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class SafeTokenProvider {
  private final ReentrantLock lock = new ReentrantLock();
  private Token token;

  private final TokenProvider tokenProvider;

  public SafeTokenProvider(final TokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  public Optional<Token> token(final Date instant) {
    lock.lock();
    try {
      if (token == null) {
        return refreshTokenFromSource(instant);
      }
      if (token.isAvailableAt(instant)) {
        return Optional.of(token);
      }
      return refreshTokenFromSource(instant);
    } finally {
      lock.unlock();
    }
  }

  private Optional<Token> refreshTokenFromSource(final Date instant) {
    final Optional<Token> possibleToken = tokenProvider.token(instant);
    if (possibleToken.isEmpty()) {
      return possibleToken;
    }
    token = possibleToken.get();
    return possibleToken;
  }
}
