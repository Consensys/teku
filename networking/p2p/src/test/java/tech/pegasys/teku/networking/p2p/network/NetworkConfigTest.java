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

package tech.pegasys.teku.networking.p2p.network;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import java.net.InetAddress;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;

class NetworkConfigTest {

  private Optional<String> advertisedIp = Optional.empty();
  private String listenIp = "0.0.0.0";

  @Test
  void getAdvertisedIp_shouldUseAdvertisedAddressWhenSet() {
    final String expected = "1.2.3.4";
    advertisedIp = Optional.of(expected);
    assertThat(createConfig().getAdvertisedIp()).isEqualTo(expected);
  }

  @Test
  void getAdvertisedIp_shouldResolveAnyLocalAdvertisedAddress() throws Exception {
    advertisedIp = Optional.of("0.0.0.0");
    final String expected = InetAddress.getLocalHost().getHostAddress();
    assertThat(createConfig().getAdvertisedIp()).isEqualTo(expected);
  }

  @Test
  void getAdvertisedIp_shouldReturnInterfaceIpWhenNotSet() {
    listenIp = "127.0.0.1";
    assertThat(createConfig().getAdvertisedIp()).isEqualTo(listenIp);
  }

  @Test
  void getAdvertisedIp_shouldResolveLocalhostIpWhenInterfaceIpIsAnyLocal() throws Exception {
    listenIp = "0.0.0.0";
    final String expected = InetAddress.getLocalHost().getHostAddress();
    assertThat(createConfig().getAdvertisedIp()).isEqualTo(expected);
  }

  @Test
  void getAdvertisedIp_shouldResolveLocalhostIpWhenInterfaceIpIsAnyLocalIpv6() throws Exception {
    listenIp = "::0";
    final String expected = InetAddress.getLocalHost().getHostAddress();
    assertThat(createConfig().getAdvertisedIp()).isEqualTo(expected);
  }

  private NetworkConfig createConfig() {
    return new NetworkConfig(
        KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1(),
        listenIp,
        advertisedIp,
        9000,
        OptionalInt.empty(),
        emptyList(),
        false,
        emptyList(),
        new TargetPeerRange(20, 30));
  }
}
