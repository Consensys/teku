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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ArtemisConfigurationTest {

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void validMinimum() {
    ArtemisConfiguration.fromString("");
  }

  @Test
  void wrongPort() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtemisConfiguration.fromString("node.identity=\"2345\"\nnode.port=100000"));
  }

  @Test
  void invalidAdvertisedPort() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtemisConfiguration.fromString("node.identity=\"2345\"\nnode.advertisedPort=100000"));
  }

  @Test
  void advertisedPortDefaultsToPort() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("node.port=1234");
    assertThat(config.getAdvertisedPort()).isEqualTo(1234);
  }

  @Test
  void invalidNetworkMode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtemisConfiguration.fromString(
                "node.identity=\"2345\"\nnode.networkMode=\"tcpblah\""));
  }

  @Test
  void invalidMinimalArtemisConfig() {
    Constants.setConstants("minimal");
    ArtemisConfiguration config = ArtemisConfiguration.fromString("deposit.numValidators=7");
    assertThrows(IllegalArgumentException.class, () -> config.validateConfig());
  }

  @Test
  void invalidMainnetArtemisConfig() {
    Constants.setConstants("mainnet");
    ArtemisConfiguration config = ArtemisConfiguration.fromString("deposit.numValidators=31");
    assertThrows(IllegalArgumentException.class, () -> config.validateConfig());
  }

  @Test
  void shouldReadRestApiSettings() {
    ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "beaconrestapi.portNumber=1\nbeaconrestapi.enableSwagger=false");
    assertEquals(config.getBeaconRestAPIPortNumber(), 1);
    assertEquals(config.getBeaconRestAPIEnableSwagger(), false);
  }
}
