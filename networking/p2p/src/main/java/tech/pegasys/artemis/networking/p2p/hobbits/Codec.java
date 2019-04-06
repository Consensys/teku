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

import com.google.common.base.Splitter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import net.consensys.cava.bytes.Bytes;

interface Codec {
  enum ProtocolType {
    GOSSIP,
    RPC
  }

  /**
   * Determines the message protocol type
   *
   * @param message
   * @return the protocol type
   */
  static ProtocolType protocolType(Bytes message) {
    Bytes requestLineBytes = null;
    for (int i = 0; i < message.size(); i++) {
      if (message.get(i) == (byte) '\n') {
        requestLineBytes = message.slice(0, i);
        break;
      }
    }
    String requestLine = new String(requestLineBytes.toArrayUnsafe(), StandardCharsets.UTF_8);
    Iterator<String> segments = Splitter.on(" ").split(requestLine).iterator();
    String protocol = segments.next();
    String version = segments.next();
    String command = segments.next();
    return ProtocolType.valueOf(command.toUpperCase());
  }
}
