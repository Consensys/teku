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

package org.ethereum.beacon.discovery.community;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.RandomPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PacketEncodingTest {
  @Test
  public void encodeRandomPacketTest() {
    RandomPacket randomPacket =
        RandomPacket.create(
            Bytes.fromHexString(
                "0x0101010101010101010101010101010101010101010101010101010101010101"),
            Bytes.fromHexString("0x020202020202020202020202"),
            Bytes.fromHexString(
                "0x0404040404040404040404040404040404040404040404040404040404040404040404040404040404040404"));
    Assertions.assertEquals(
        Bytes.fromHexString(
            "0x01010101010101010101010101010101010101010101010101010101010101018c0202020202020202020202020404040404040404040404040404040404040404040404040404040404040404040404040404040404040404"),
        randomPacket.getBytes());
  }

  @Test
  public void encodeWhoAreYouTest() {
    WhoAreYouPacket whoAreYouPacket =
        WhoAreYouPacket.create(
            Bytes.fromHexString(
                "0x0101010101010101010101010101010101010101010101010101010101010101"),
            Bytes.fromHexString("0x020202020202020202020202"),
            Bytes.fromHexString(
                "0x0303030303030303030303030303030303030303030303030303030303030303"),
            UInt64.valueOf(1));
    Assertions.assertEquals(
        Bytes.fromHexString(
            "0101010101010101010101010101010101010101010101010101010101010101ef8c020202020202020202020202a0030303030303030303030303030303030303030303030303030303030303030301"),
        whoAreYouPacket.getBytes());
  }

  @Test
  public void encodeAuthPacketTest() {
    Bytes tag =
        Bytes.fromHexString("0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903");
    Bytes authTag = Bytes.fromHexString("0x27b5af763c446acd2749fe8e");
    Bytes idNonce =
        Bytes.fromHexString("0xe551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c65");
    Bytes ephemeralPubkey =
        Bytes.fromHexString(
            "0xb35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81");
    Bytes authRespCiphertext =
        Bytes.fromHexString(
            "0x570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852");
    Bytes messageCiphertext = Bytes.fromHexString("0xa5d12a2d94b8ccb3ba55558229867dc13bfa3648");
    Bytes authHeader =
        AuthHeaderMessagePacket.encodeAuthHeaderRlp(
            authTag, idNonce, ephemeralPubkey, authRespCiphertext);
    AuthHeaderMessagePacket authHeaderMessagePacket =
        AuthHeaderMessagePacket.create(tag, authHeader, messageCiphertext);

    Assertions.assertEquals(
        Bytes.fromHexString(
            "0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903f8cc8c27b5af763c446acd2749fe8ea0e551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c658367636db840b35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81b856570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852a5d12a2d94b8ccb3ba55558229867dc13bfa3648"),
        authHeaderMessagePacket.getBytes());
  }

  @Test
  public void encodeMessagePacketTest() {
    MessagePacket messagePacket =
        MessagePacket.create(
            Bytes.fromHexString(
                "0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903"),
            Bytes.fromHexString("0x27b5af763c446acd2749fe8e"),
            Bytes.fromHexString("0xa5d12a2d94b8ccb3ba55558229867dc13bfa3648"));

    Assertions.assertEquals(
        Bytes.fromHexString(
            "0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f421079038c27b5af763c446acd2749fe8ea5d12a2d94b8ccb3ba55558229867dc13bfa3648"),
        messagePacket.getBytes());
  }
}
