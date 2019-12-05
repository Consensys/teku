package tech.pegasys.artemis.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import okhttp3.OkHttpClient;
import okhttp3.Request.Builder;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SimpleHttpClient {
  private final OkHttpClient httpClient = new OkHttpClient();

  public String get(final URI baseUrl, final String path) throws IOException {
    final Response response = httpClient.newCall(
        new Builder()
            .url(baseUrl.resolve(path).toURL())
            .get()
            .build()).execute();
    assertThat(response.isSuccessful()).isTrue();
    final ResponseBody body = response.body();
    assertThat(body).isNotNull();
    return body.string();
  }
}
