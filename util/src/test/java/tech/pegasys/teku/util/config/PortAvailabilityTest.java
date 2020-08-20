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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import org.junit.jupiter.api.Test;

public class PortAvailabilityTest {

  @Test
  void isValidPort_shouldRejectBelowBounds() {
    assertThat(PortAvailability.isPortValid(-1)).isFalse();
  }

  @Test
  void isValidPort_shouldRejectAboveBounds() {
    assertThat(PortAvailability.isPortValid(65536)).isFalse();
  }

  @Test
  void isValidPort_shouldBeValidInsideBounds() {
    assertThat(PortAvailability.isPortValid(0)).isTrue();
    assertThat(PortAvailability.isPortValid(1)).isTrue();
    assertThat(PortAvailability.isPortValid(1025)).isTrue();
    assertThat(PortAvailability.isPortValid(65535)).isTrue();
  }

  @Test
  void shouldDetectPortNotAvailableForTcp() throws IOException {
    try (final ServerSocket serverSocket = new ServerSocket(0)) {
      final int port = serverSocket.getLocalPort();
      assertThat(PortAvailability.isPortAvailableForTcp(port)).isFalse();
      assertThat(PortAvailability.isPortAvailable(port)).isFalse();
    }
  }

  @Test
  void shouldDetectPortAvailableForTcp() {
    final int port = 0;
    assertThat(PortAvailability.isPortAvailableForTcp(port)).isTrue();
    assertThat(PortAvailability.isPortAvailable(port)).isTrue();
  }

  @Test
  void shouldDetectPortAvailableForUdp() {
    final int port = 0;
    assertThat(PortAvailability.isPortAvailableForUdp(port)).isTrue();
    assertThat(PortAvailability.isPortAvailable(port)).isTrue();
  }

  @Test
  void shouldDetectPortNotAvailableForUdp() throws SocketException {
    try (final DatagramSocket datagramSocket = new DatagramSocket(0)) {
      final int port = datagramSocket.getPort();
      assertThat(PortAvailability.isPortAvailableForUdp(port)).isFalse();
      assertThat(PortAvailability.isPortAvailable(port)).isFalse();
    }
  }
}
