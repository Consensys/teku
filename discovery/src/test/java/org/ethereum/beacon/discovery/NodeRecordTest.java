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

package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.TestUtil.SEED;
import static org.ethereum.beacon.discovery.enr.NodeRecord.FIELD_IP_V4;
import static org.ethereum.beacon.discovery.enr.NodeRecord.FIELD_PKEY_SECP256K1;
import static org.ethereum.beacon.discovery.enr.NodeRecord.FIELD_TCP_V4;
import static org.ethereum.beacon.discovery.enr.NodeRecord.FIELD_UDP_V4;
import static org.ethereum.beacon.util.Utils.extractBytesFromUnsignedBigInt;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.enr.EnrScheme;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.enr.NodeRecordFactory;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.uint.UInt64;

/**
 * ENR serialization/deserialization test
 *
 * <p>ENR - Ethereum Node Record, according to <a
 * href="https://eips.ethereum.org/EIPS/eip-778">https://eips.ethereum.org/EIPS/eip-778</a>
 */
public class NodeRecordTest {
  private static final NodeRecordFactory NODE_RECORD_FACTORY = NodeRecordFactory.DEFAULT;

  @Test
  public void testLocalhostV4() throws Exception {
    final String expectedHost = "127.0.0.1";
    final Integer expectedUdpPort = 30303;
    final Integer expectedTcpPort = null;
    final UInt64 expectedSeqNumber = UInt64.valueOf(1);
    final BytesValue expectedPublicKey =
        BytesValue.fromHexString(
            "03ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138");
    final BytesValue expectedSignature =
        BytesValue.fromHexString(
            "7098ad865b00a582051940cb9cf36836572411a47278783077011599ed5cd16b76f2635f4e234738f30813a89eb9137e3e3df5266e3a1f11df72ecf1145ccb9c");

    final String localhostEnr =
        "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";
    NodeRecord nodeRecord = NODE_RECORD_FACTORY.fromBase64(localhostEnr);

    assertEquals(EnrScheme.V4, nodeRecord.getIdentityScheme());
    assertArrayEquals(
        InetAddress.getByName(expectedHost).getAddress(),
        ((Bytes) nodeRecord.get(NodeRecord.FIELD_IP_V4)).toArray());
    assertEquals(expectedUdpPort, nodeRecord.get(NodeRecord.FIELD_UDP_V4));
    assertEquals(expectedTcpPort, nodeRecord.get(NodeRecord.FIELD_TCP_V4));
    assertEquals(expectedSeqNumber, nodeRecord.getSeq());
    Object key = nodeRecord.get(NodeRecord.FIELD_PKEY_SECP256K1);
    if (key instanceof Bytes) {
      assertArrayEquals(expectedPublicKey.extractArray(), ((Bytes)key).toArray());
    } else {
      assertArrayEquals(expectedPublicKey.extractArray(), ((BytesValue)key).extractArray());
    }

    assertArrayEquals(expectedSignature.extractArray(), nodeRecord.getSignature().toArray());

    String localhostEnrRestored = nodeRecord.asBase64();
    // The order of fields is not strict so we don't compare strings
    NodeRecord nodeRecordRestored = NODE_RECORD_FACTORY.fromBase64(localhostEnrRestored);

    assertEquals(EnrScheme.V4, nodeRecordRestored.getIdentityScheme());
    NodeRecord nodeRecordV4Restored = (NodeRecord) nodeRecordRestored;
    Object key1 = nodeRecordV4Restored.get(NodeRecord.FIELD_IP_V4);
    if (key1 instanceof Bytes) {
      assertArrayEquals(
          InetAddress.getByName(expectedHost).getAddress(),
          ((Bytes) key1).toArray()
          );
    } else {
      assertArrayEquals(
          InetAddress.getByName(expectedHost).getAddress(),
          ((BytesValue) key1).extractArray()
          );
    }
    assertEquals(expectedUdpPort, nodeRecordV4Restored.get(NodeRecord.FIELD_UDP_V4));
    assertEquals(expectedTcpPort, nodeRecordV4Restored.get(NodeRecord.FIELD_TCP_V4));
    assertEquals(expectedSeqNumber, nodeRecordV4Restored.getSeq());
    Object key2 = nodeRecordV4Restored.get(NodeRecord.FIELD_PKEY_SECP256K1);
    if (key2 instanceof Bytes) {
      assertArrayEquals(expectedPublicKey.extractArray(), ((Bytes)key2).toArray());
    } else {
      assertArrayEquals(expectedPublicKey.extractArray(), ((BytesValue)key2).extractArray());
    }
    assertArrayEquals(expectedSignature.extractArray(), nodeRecordV4Restored.getSignature().toArray());
  }

  @Test
  public void testSignature() throws Exception {
    Random rnd = new Random(SEED);
    byte[] privKey = new byte[32];
    rnd.nextBytes(privKey);
    ECKeyPair keyPair = ECKeyPair.create(privKey);
    BytesValue localIp = BytesValue.wrap(InetAddress.getByName("127.0.0.1").getAddress());
    NodeRecord nodeRecord0 =
        NodeRecordFactory.DEFAULT.createFromValues(
            EnrScheme.V4,
            UInt64.ZERO,
            Bytes.EMPTY,
            Pair.with(FIELD_IP_V4, localIp),
            Pair.with(FIELD_TCP_V4, 30303),
            Pair.with(FIELD_UDP_V4, 30303),
            Pair.with(
                FIELD_PKEY_SECP256K1,
                BytesValue.wrap(extractBytesFromUnsignedBigInt(keyPair.getPublicKey()))));
    Bytes signature0 = Functions.sign(Bytes.wrap(privKey), nodeRecord0.serialize(false));
    nodeRecord0.setSignature(signature0);
    nodeRecord0.verify();
    NodeRecord nodeRecord1 =
        NodeRecordFactory.DEFAULT.createFromValues(
            EnrScheme.V4,
            UInt64.valueOf(1),
            Bytes.EMPTY,
            Pair.with(FIELD_IP_V4, localIp),
            Pair.with(FIELD_TCP_V4, 30303),
            Pair.with(FIELD_UDP_V4, 30303),
            Pair.with(
                FIELD_PKEY_SECP256K1,
                BytesValue.wrap(extractBytesFromUnsignedBigInt(keyPair.getPublicKey()))));
    Bytes signature1 = Functions.sign(Bytes.wrap(privKey), nodeRecord1.serialize(false));
    nodeRecord1.setSignature(signature1);
    nodeRecord1.verify();
    assertNotEquals(nodeRecord0.serialize(), nodeRecord1.serialize());
    assertNotEquals(signature0, signature1);
    nodeRecord1.setSignature(nodeRecord0.getSignature());
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    try {
      nodeRecord1.verify();
    } catch (AssertionError ex) {
      exceptionThrown.set(true);
    }
    assertTrue(exceptionThrown.get());
  }
}
