package tech.pegasys.teku.infrastructure.io.resource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class URLResourceLoaderIntegrationTest {

  @Test
  void shouldThrowConnectExceptionWhenConnectionTimesOut() throws Exception {
    // Create a socket on any available port that never responds
    final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
    try (final ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
      final ResourceLoader loader = new URLResourceLoader(Optional.empty(), __ -> true, 1);
      assertThatThrownBy(
              () ->
                  loader.loadSource(
                      "http://"
                          + loopbackAddress.getHostAddress()
                          + ":"
                          + serverSocket.getLocalPort()))
          .isInstanceOf(SocketTimeoutException.class);
    }
  }
}
