/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ArtemisConfigurationTest {

  @Test
  void missingIdentity() {
    assertThrows(IllegalArgumentException.class, () -> ArtemisConfiguration.fromString(""));
  }

  @Test
  void validMinimum() {
    ArtemisConfiguration.fromString("identity=\"a3e4b1c5\"");
  }

  @Test
  void wrongPort() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtemisConfiguration.fromString("identity=\"2345\"\nport=100000"));
  }

  @Test
  void invalidAdvertisedPort() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtemisConfiguration.fromString("identity=\"2345\"\nadvertisedPort=100000"));
  }

  @Test
  void validPeer() {
    ArtemisConfiguration.fromString(
        "identity=\"2345\"\npeers=[\"enode://a0b0e0f099@localhost:9000\"]");
  }

  @Test
  void invalidPeer() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtemisConfiguration.fromString(
                "identity=\"2345\"\npeers=[\"enode://localhost:9000\"]"));
  }

  @Test
  void invalidNetworkMode() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtemisConfiguration.fromString("identity=\"2345\"\nnetworkMode=\"tcpblah\""));
  }
}
