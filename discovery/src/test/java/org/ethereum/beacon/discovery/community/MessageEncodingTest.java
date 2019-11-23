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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessageEncodingTest {
  @Test
  public void encodePing() {
    PingMessage pingMessage =
        new PingMessage(
            Bytes.wrap(UInt64.valueOf(1).toBigInteger().toByteArray()), UInt64.valueOf(1));
    Assertions.assertEquals(Bytes.fromHexString("0x01c20101"), pingMessage.getBytes());
  }

  @Test
  public void encodePong() throws Exception {
    PongMessage pongMessage =
        new PongMessage(
            Bytes.wrap(UInt64.valueOf(1).toBigInteger().toByteArray()),
            UInt64.valueOf(1),
            Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress()),
            5000);
    Assertions.assertEquals(
        Bytes.fromHexString("0x02ca0101847f000001821388"), pongMessage.getBytes());
  }

  @Test
  public void encodeFindNode() {
    FindNodeMessage findNodeMessage =
        new FindNodeMessage(Bytes.wrap(UInt64.valueOf(1).toBigInteger().toByteArray()), 256);
    Assertions.assertEquals(Bytes.fromHexString("0x03c401820100"), findNodeMessage.getBytes());
  }

  @Test
  public void encodeNodes() {
    NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
    NodesMessage nodesMessage =
        new NodesMessage(
            Bytes.wrap(UInt64.valueOf(1).toBigInteger().toByteArray()),
            2,
            () -> {
              List<NodeRecord> nodeRecords = new ArrayList<>();
              nodeRecords.add(
                  nodeRecordFactory.fromBase64(
                      "-HW4QBzimRxkmT18hMKaAL3IcZF1UcfTMPyi3Q1pxwZZbcZVRI8DC5infUAB_UauARLOJtYTxaagKoGmIjzQxO2qUygBgmlkgnY0iXNlY3AyNTZrMaEDymNMrg1JrLQB2KTGtv6MVbcNEVv0AHacwUAPMljNMTg"));
              nodeRecords.add(
                  nodeRecordFactory.fromBase64(
                      "-HW4QNfxw543Ypf4HXKXdYxkyzfcxcO-6p9X986WldfVpnVTQX1xlTnWrktEWUbeTZnmgOuAY_KUhbVV1Ft98WoYUBMBgmlkgnY0iXNlY3AyNTZrMaEDDiy3QkHAxPyOgWbxp5oF1bDdlYE6dLCUUp8xfVw50jU"));
              return nodeRecords;
            },
            2);
    Assertions.assertEquals(
        Bytes.fromHexString(
            "0x04f8f20102f8eef875b8401ce2991c64993d7c84c29a00bdc871917551c7d330fca2dd0d69c706596dc655448f030b98a77d4001fd46ae0112ce26d613c5a6a02a81a6223cd0c4edaa53280182696482763489736563703235366b31a103ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd3138f875b840d7f1c39e376297f81d7297758c64cb37dcc5c3beea9f57f7ce9695d7d5a67553417d719539d6ae4b445946de4d99e680eb8063f29485b555d45b7df16a1850130182696482763489736563703235366b31a1030e2cb74241c0c4fc8e8166f1a79a05d5b0dd95813a74b094529f317d5c39d235"),
        nodesMessage.getBytes());
  }
}
