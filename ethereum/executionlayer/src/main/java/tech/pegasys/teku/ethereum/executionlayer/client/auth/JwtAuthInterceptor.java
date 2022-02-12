package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

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
