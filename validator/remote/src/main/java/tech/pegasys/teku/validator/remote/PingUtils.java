package tech.pegasys.teku.validator.remote;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import okhttp3.HttpUrl;

public class PingUtils {

  private static final int PING_TIMEOUT_MILLIS = 1000;

  public static boolean hostIsReachable(final HttpUrl hostUrl) {
    try (final Socket socket = new Socket()) {
      final InetSocketAddress socketAddress =
          new InetSocketAddress(InetAddress.getByName(hostUrl.host()), hostUrl.port());
      socket.connect(socketAddress, PING_TIMEOUT_MILLIS);
      return true;
    } catch (IOException ex) {
      return false;
    }
  }
}
