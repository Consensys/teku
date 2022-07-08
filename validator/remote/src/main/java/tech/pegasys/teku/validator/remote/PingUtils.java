/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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
