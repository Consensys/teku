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

package tech.pegasys.artemis.networking.p2p.hobbits;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Representation of a RPC message that was received from a remote peer. */
public final class RPCMessage {

  private final long requestId;
  private final RPCMethod method;
  private final JsonNode body;
  private final int length;

  public RPCMessage(long requestId, RPCMethod method, JsonNode body, int length) {
    this.requestId = requestId;
    this.method = method;
    this.body = body;
    this.length = length;
  }

  /** @return the request identifier */
  public long requestId() {
    return requestId;
  }

  /** @return the method used by the RPC call. */
  public RPCMethod method() {
    return method;
  }

  /**
   * Reads the body of the message into a
   *
   * @param T the type of the body to unmarshall
   * @param <T> the type of the body to unmarshall
   * @return the body, unmarshalled.
   * @throws UncheckedIOException if the body cannot be successfully unmarshalled
   */
  public <T> T bodyAs(Class<T> T) {
    try {
      return RPCCodec.mapper.treeToValue(body, T);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** @return the length of the message in bytes */
  public int length() {
    return length;
  }
}
