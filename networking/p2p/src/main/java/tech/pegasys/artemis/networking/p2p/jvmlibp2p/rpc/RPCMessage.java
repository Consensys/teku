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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.alogger.ALogger;

/** Representation of a RPC message that was received from a remote peer. */
public final class RPCMessage {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final JsonNode body;

  public RPCMessage(JsonNode body) {
    this.body = body;
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

  /**
   * Reads the body of the message into a List
   *
   * @param T the type of the body to unmarshall
   * @param <T> the type of the body to unmarshall
   * @return the body, unmarshalled.
   * @throws UncheckedIOException if the body cannot be successfully unmarshalled
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> bodyAsList(Class<T> T) {
    try {
      return (List<T>) RPCCodec.mapper.treeToValue(body, T);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads the body of the message into Bytes
   *
   * @return the body, unmarshalled.
   * @throws UncheckedIOException if the body cannot be successfully unmarshalled
   */
  public Bytes bodyAsBytes() {
    try {
      return Bytes.wrap(body.binaryValue());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads the body of the message into List<Bytes>
   *
   * @return the body, unmarshalled.
   * @throws UncheckedIOException if the body cannot be successfully unmarshalled
   */
  public List<Bytes> bodyAsBytesList() {
    List<Bytes> newList = new ArrayList<>();
    try {
      if (body.isArray()) {
        for (final JsonNode objNode : body) {
          newList.add(Bytes.wrap(objNode.binaryValue()));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return newList;
  }

  public JsonNode getBody() {
    return body;
  }
}
