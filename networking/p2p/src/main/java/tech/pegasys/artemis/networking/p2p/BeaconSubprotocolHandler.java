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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;
import java.time.Instant;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.rlpx.RLPxService;
import net.consensys.cava.rlpx.wire.SubProtocolHandler;

final class BeaconSubprotocolHandler implements SubProtocolHandler {

  private static final ObjectMapper mapper = new ObjectMapper(new BsonFactory());

  private final RLPxService service;

  BeaconSubprotocolHandler(RLPxService service) {
    this.service = service;
  }

  @Override
  public AsyncCompletion handle(String connectionId, int messageType, Bytes message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AsyncCompletion handleNewPeerConnection(String connectionId) {
    try {
      Bytes bytes =
          Bytes.wrap(
              mapper
                  .writerFor(PingMessage.class)
                  .writeValueAsBytes(new PingMessage(Instant.now().toEpochMilli())));
      service.send(BeaconSubprotocol.BEACON_ID, 0, connectionId, bytes);
      return AsyncCompletion.completed();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public AsyncCompletion stop() {
    return AsyncCompletion.completed();
  }
}
