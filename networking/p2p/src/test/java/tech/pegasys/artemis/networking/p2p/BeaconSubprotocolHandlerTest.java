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

package tech.pegasys.artemis.networking.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;
import java.net.InetSocketAddress;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.crypto.SECP256K1;
import net.consensys.cava.rlpx.RLPxService;
import net.consensys.cava.rlpx.WireConnectionRepository;
import net.consensys.cava.rlpx.wire.DisconnectReason;
import net.consensys.cava.rlpx.wire.SubProtocolIdentifier;
import org.junit.jupiter.api.Test;

final class BeaconSubprotocolHandlerTest {

  private static final ObjectMapper mapper = new ObjectMapper(new BsonFactory());

  private int messageType;
  private Bytes messageCaptured;

  private RLPxService mockService =
      new RLPxService() {

        @Override
        public AsyncCompletion connectTo(
            SECP256K1.PublicKey peerPublicKey, InetSocketAddress peerAddress) {
          return null;
        }

        @Override
        public AsyncCompletion start() {
          return null;
        }

        @Override
        public AsyncCompletion stop() {
          return null;
        }

        @Override
        public void send(
            SubProtocolIdentifier subProtocolIdentifier,
            int messageType,
            String connectionId,
            Bytes message) {
          BeaconSubprotocolHandlerTest.this.messageType = messageType;
          messageCaptured = message;
        }

        @Override
        public void broadcast(
            SubProtocolIdentifier subProtocolIdentifier, int messageType, Bytes message) {}

        @Override
        public void disconnect(String connectionId, DisconnectReason reason) {}

        @Override
        public WireConnectionRepository repository() {
          return null;
        }
      };

  @Test
  void handleNewPeerConnection() throws Exception {
    BeaconSubprotocolHandler handler = new BeaconSubprotocolHandler(mockService);
    handler.handleNewPeerConnection("abc");
    assertEquals(0, messageType);
    PingMessage ping =
        mapper.readerFor(PingMessage.class).readValue(messageCaptured.toArrayUnsafe());
    assertNotNull(ping.timestamp());
  }
}
