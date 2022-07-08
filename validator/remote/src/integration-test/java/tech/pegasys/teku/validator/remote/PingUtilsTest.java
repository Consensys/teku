package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingUtilsTest {

  private final MockWebServer mockWebServer = new MockWebServer();

  private HttpUrl hostUrl;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    this.hostUrl = mockWebServer.url("/");
  }

  @Test
  void reachesHost() throws IOException {
    assertThat(PingUtils.hostIsReachable(hostUrl)).isTrue();
    mockWebServer.close();
  }

  @Test
  void doesNotReachHost() throws IOException {
    mockWebServer.close();
    assertThat(PingUtils.hostIsReachable(hostUrl)).isFalse();
  }
}
