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

package tech.pegasys.teku.util.config;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PortAvailability {
  private static final Logger LOG = LogManager.getLogger();

  public static boolean isPortAvailableForTcp(final int port) {
    if (!isPortValid(port)) {
      return false;
    }
    ServerSocket serverSocket = null;
    try {
      serverSocket = new ServerSocket(port);
      serverSocket.setReuseAddress(true);
      return true;
    } catch (IOException ex) {
      LOG.trace("failed to open port for TCP", ex);
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException ex) {
          LOG.trace("failed to close port", ex);
        }
      }
    }
    return false;
  }

  public static boolean isPortAvailableForUdp(final int port) {
    if (!isPortValid(port)) {
      return false;
    }
    DatagramSocket datagramSocket = null;
    try {
      datagramSocket = new DatagramSocket(port);
      datagramSocket.setReuseAddress(true);
      return true;
    } catch (IOException ex) {
      LOG.trace("failed to open port for UDP", ex);
    } finally {
      if (datagramSocket != null) {
        datagramSocket.close();
      }
    }
    return false;
  }

  public static boolean isPortValid(final int port) {
    return (port >= 1 && port <= 65535);
  }

  public static boolean isPortAvailable(final int port) {
    return isPortAvailableForTcp(port) && isPortAvailableForUdp(port);
  }
}
