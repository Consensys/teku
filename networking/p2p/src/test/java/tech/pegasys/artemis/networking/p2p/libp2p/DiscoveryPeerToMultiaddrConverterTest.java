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

package tech.pegasys.artemis.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;

class DiscoveryPeerToMultiaddrConverterTest {
  private static final NodeId NODE_ID = new MockNodeId(Bytes.fromHexString("0x1234", 32));

  @Test
  public void shouldConvertIpV4Peer() throws Exception {
    final byte[] ipAddress = {123, 34, 58, 22};
    final int port = 5883;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            NODE_ID, new InetSocketAddress(InetAddress.getByAddress(ipAddress), port));
    final Multiaddr result = DiscoveryPeerToMultiaddrConverter.convertToMultiAddr(peer);
    assertThat(result)
        .isEqualTo(Multiaddr.fromString("/ip4/123.34.58.22/tcp/5883/p2p/" + NODE_ID.toBase58()));
    assertThat(result.getComponent(Protocol.IP4)).isEqualTo(ipAddress);
    assertThat(result.getComponent(Protocol.TCP)).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThat(result.getComponent(Protocol.P2P)).isEqualTo(NODE_ID.toBytes().toArrayUnsafe());
  }

  @Test
  public void shouldConvertIpV6Peer() throws Exception {
    final byte[] ipAddress = Bytes.fromHexString("0x33000004500007800000001200000001").toArray();
    final int port = 5883;
    final DiscoveryPeer peer =
        new DiscoveryPeer(
            NODE_ID, new InetSocketAddress(InetAddress.getByAddress(ipAddress), port));
    final Multiaddr result = DiscoveryPeerToMultiaddrConverter.convertToMultiAddr(peer);
    assertThat(result)
        .isEqualTo(
            Multiaddr.fromString(
                "/ip6/3300:4:5000:780:0:12:0:1/tcp/5883/p2p/" + NODE_ID.toBase58()));
    assertThat(result.getComponent(Protocol.IP6)).isEqualTo(ipAddress);
    assertThat(result.getComponent(Protocol.TCP)).isEqualTo(Protocol.TCP.addressToBytes("5883"));
    assertThat(result.getComponent(Protocol.P2P)).isEqualTo(NODE_ID.toBytes().toArrayUnsafe());
  }
}
