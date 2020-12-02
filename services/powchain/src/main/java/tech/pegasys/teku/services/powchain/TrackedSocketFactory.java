/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.powchain;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import javax.net.SocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TrackedSocketFactory extends SocketFactory {
  private static final Logger LOG = LogManager.getLogger();
  private volatile Set<Socket> socketSet = new ConcurrentSkipListSet<>();
  private final SocketFactory delegate;

  public TrackedSocketFactory(final SocketFactory delegate) {
    this.delegate = delegate;
  }

  public void closeAllSockets() {
    cleanup();
    socketSet.forEach(
        socket -> {
          try {
            socket.close();
          } catch (IOException e) {
            LOG.trace("Could not close socket " + socket.toString(), e);
          }
        });
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    cleanup();
    final Socket socket = delegate.createSocket(host, port);
    socketSet.add(socket);
    return socket;
  }

  @Override
  public Socket createSocket(
      final String host, final int port, final InetAddress localAddress, final int localPort)
      throws IOException, UnknownHostException {
    cleanup();
    final Socket socket = delegate.createSocket(host, port, localAddress, localPort);
    socketSet.add(socket);
    return socket;
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    cleanup();
    final Socket socket = delegate.createSocket(host, port);
    socketSet.add(socket);
    return socket;
  }

  @Override
  public Socket createSocket(
      final InetAddress address,
      final int port,
      final InetAddress localAddress,
      final int localPort)
      throws IOException {
    cleanup();
    final Socket socket = delegate.createSocket(address, port, localAddress, localPort);
    socketSet.add(socket);
    return socket;
  }

  private void cleanup() {
    socketSet.removeIf(Socket::isClosed);
  }
}
