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

package tech.pegasys.artemis.networking.p2p.libp2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.networking.p2p.libp2p.discovery.discv5.NodeRecordConverter.convertToDiscoveryPeer;

import io.libp2p.core.multiformats.Multiaddr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.libp2p.discovery.DiscoveryPeer;

class NodeRecordConverterTest {

  private static final DiscoveryNodeId NODE_ID =
      new DiscoveryNodeId(
          Bytes.fromHexString(
              "0x833246C198388F1B5E06EF1950B0A6705FBF6370E002656CDA2C6C803C06258D"));
  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x0295A5A50F083697FF8557F3C6FE0CDF8E8EC2141D15F19A5A45571ED9C38CE181");
  private static final Bytes IPV6_LOCALHOST =
      Bytes.fromHexString("0x00000000000000000000000000000001");

  @Test
  public void shouldNotConvertRecordWithNoIp() {
    assertThat(convertNodeRecordWithFields()).isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithIpButNoPort() {
    assertThat(
            convertNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1}))))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithIpAndUdpPortButNoTcpPort() {
    assertThat(
            convertNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.UDP_V4, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithV4IpAndV6Port() {
    assertThat(
            convertNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.TCP_V6, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithV6IpAndV4Port() {
    assertThat(
            convertNodeRecordWithFields(
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V4, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotConvertRecordWithPortButNoIp() {
    assertThat(convertNodeRecordWithFields(new EnrField(EnrField.TCP_V4, 30303))).isEmpty();
  }

  @Test
  public void shouldConvertIpV4Record() {
    // IP address bytes are unsigned. Make sure we handle that correctly.
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {-127, 24, 31, 22})),
            new EnrField(EnrField.TCP_V4, 1234));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                NODE_ID,
                Multiaddr.fromString("/ip4/129.24.31.22/tcp/1234/p2p/" + NODE_ID.toBase58())));
  }

  @Test
  public void shouldConvertIpV6Record() {
    final Optional<DiscoveryPeer> result =
        convertNodeRecordWithFields(
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 1234));
    assertThat(result)
        .contains(
            new DiscoveryPeer(
                NODE_ID, Multiaddr.fromString("/ip6/::1/tcp/1234/p2p/" + NODE_ID.toBase58())));
  }

  private Optional<DiscoveryPeer> convertNodeRecordWithFields(final EnrField... fields) {
    return convertToDiscoveryPeer(createNodeRecord(fields));
  }

  private NodeRecord createNodeRecord(final EnrField... fields) {
    final ArrayList<EnrField> fieldList = new ArrayList<>(Arrays.asList(fields));
    fieldList.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fieldList.add(new EnrField(EnrField.PKEY_SECP256K1, PUB_KEY));
    return NodeRecordFactory.DEFAULT.createFromValues(UInt64.ZERO, fieldList);
  }
}
