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
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.IdentityScheme;
import org.ethereum.beacon.discovery.enr.NodeRecordFactory;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

// import tech.pegasys.artemis.util.bytes.Bytes8;
// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.uint.UInt64;

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
  public IdentityScheme getIdentityScheme() {
    return IdentityScheme.V5;
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
          return new PingMessage(
              Bytes.wrap(((RlpString) payload.get(0)).getBytes()),
              UInt64.fromBytes(Bytes.wrap(((RlpString) payload.get(1)).getBytes())));
        }
      case PONG:
        {
          return new PongMessage(
              Bytes.wrap(((RlpString) payload.get(0)).getBytes()),
              UInt64.fromBytes(Bytes.wrap(((RlpString) payload.get(1)).getBytes())),
              Bytes.wrap(((RlpString) payload.get(2)).getBytes()),
              ((RlpString) payload.get(3)).asPositiveBigInteger().intValueExact());
        }
      case FINDNODE:
        {
          return new FindNodeMessage(
              Bytes.wrap(((RlpString) payload.get(0)).getBytes()),
              ((RlpString) payload.get(1)).asPositiveBigInteger().intValueExact());
        }
      case NODES:
        {
          RlpList nodeRecords = (RlpList) payload.get(2);
          return new NodesMessage(
              Bytes.wrap(((RlpString) payload.get(0)).getBytes()),
              ((RlpString) payload.get(1)).asPositiveBigInteger().intValueExact(),
              () ->
                  nodeRecords.getValues().stream()
                      .map(rs -> nodeRecordFactory.fromBytes(((RlpString) rs).getBytes()))
                      .collect(Collectors.toList()),
              nodeRecords.getValues().size());
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
