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

package org.ethereum.beacon.discovery.message;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.Protocol;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class DiscoveryV5Message implements DiscoveryMessage {
  private final Bytes bytes;
  private List<RlpType> payload = null;

  public DiscoveryV5Message(Bytes bytes) {
    this.bytes = bytes;
  }

  public static DiscoveryV5Message from(V5Message v5Message) {
    return new DiscoveryV5Message(v5Message.getBytes());
  }

  @Override
  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public Protocol getProtocol() {
    return Protocol.V5;
  }

  public MessageCode getCode() {
    return MessageCode.fromNumber(getBytes().get(0));
  }

  private synchronized void decode() {
    if (payload != null) {
      return;
    }
    this.payload =
        ((RlpList) RlpDecoder.decode(getBytes().slice(1).toArray()).getValues().get(0)).getValues();
  }

  public Bytes getRequestId() {
    decode();
    return Bytes.wrap(((RlpString) payload.get(0)).getBytes());
  }

  public V5Message create(NodeRecordFactory nodeRecordFactory) {
    decode();
    MessageCode code = MessageCode.fromNumber(getBytes().get(0));
    switch (code) {
      case PING:
        {
          return PingMessage.fromRlp(payload);
        }
      case PONG:
        {
          return PongMessage.fromRlp(payload);
        }
      case FINDNODE:
        {
          return FindNodeMessage.fromRlp(payload);
        }
      case NODES:
        {
          return NodesMessage.fromRlp(payload, nodeRecordFactory);
        }
      default:
        {
          throw new RuntimeException(
              String.format(
                  "Creation of discovery V5 messages from code %s is not supported", code));
        }
    }
  }

  @Override
  public String toString() {
    return "DiscoveryV5Message{" + "code=" + getCode() + ", bytes=" + getBytes() + '}';
  }
}
