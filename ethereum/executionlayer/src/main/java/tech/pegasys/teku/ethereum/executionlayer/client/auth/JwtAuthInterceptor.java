package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JwtAuthInterceptor implements Interceptor {
  private String jwtToken;

  public JwtAuthInterceptor() {}

  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  @NotNull
  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Request original = chain.request();

    final String authHeader = String.format("%s", jwtToken);
    Request.Builder builder = original.newBuilder().header("Authorization", authHeader);

    final Request request = builder.build();
    return chain.proceed(request);
  }
}
