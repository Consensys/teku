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

import java.util.HashMap;
import java.util.Map;

/**
 * Discovery protocol message types as described in <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#protocol-messages">https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#protocol-messages</a>
 */
public enum MessageCode {

  /**
   * PING checks whether the recipient is alive and informs it about the sender's ENR sequence
   * number.
   */
  PING(0x01),

  /** PONG is the reply to PING. */
  PONG(0x02),

  /** FINDNODE queries for nodes at the given logarithmic distance from the recipient's node ID. */
  FINDNODE(0x03),

  /**
   * NODES is the response to a FINDNODE or TOPICQUERY message. Multiple NODES messages may be sent
   * as responses to a single query.
   */
  NODES(0x04),

  /** Request for {@link #TICKET} by topic. */
  REQTICKET(0x05),

  /**
   * TICKET is the response to REQTICKET. It contains a ticket which can be used to register for the
   * requested topic.
   */
  TICKET(0x06),

  /**
   * REGTOPIC registers the sender for the given topic with a ticket. The ticket must be valid and
   * its waiting time must have elapsed before using the ticket.
   */
  REGTOPIC(0x07),

  /** REGCONFIRMATION is the response to REGTOPIC. */
  REGCONFIRMATION(0x08),

  /**
   * TOPICQUERY requests nodes in the topic queue of the given topic. The response is a NODES
   * message containing node records registered for the topic.
   */
  TOPICQUERY(0x09);

  private static final Map<Integer, MessageCode> codeMap = new HashMap<>();

  static {
    for (MessageCode type : MessageCode.values()) {
      codeMap.put(type.code, type);
    }
  }

  private int code;

  MessageCode(int code) {
    this.code = code;
  }

  public static MessageCode fromNumber(int i) {
    return codeMap.get(i);
  }

  public byte byteCode() {
    return (byte) code;
  }
}
