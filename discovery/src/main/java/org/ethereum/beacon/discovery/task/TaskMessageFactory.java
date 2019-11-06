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

package org.ethereum.beacon.discovery.task;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class TaskMessageFactory {
  public static final int DEFAULT_DISTANCE = 10;

  public static MessagePacket createPacketFromRequest(
      NodeSession.RequestInfo requestInfo, Bytes authTag, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPingPacket(authTag, session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          return createFindNodePacket(authTag, session, requestInfo.getRequestId());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static V5Message createMessageFromRequest(
      NodeSession.RequestInfo requestInfo, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPing(session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          return createFindNode(requestInfo.getRequestId());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static MessagePacket createPingPacket(
      Bytes authTag, NodeSession session, Bytes requestId) {

    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(createPing(session, requestId)));
  }

  public static PingMessage createPing(NodeSession session, Bytes requestId) {
    return new PingMessage(requestId, session.getNodeRecord().getSeq());
  }

  public static MessagePacket createFindNodePacket(
      Bytes authTag, NodeSession session, Bytes requestId) {
    FindNodeMessage findNodeMessage = createFindNode(requestId);
    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(findNodeMessage));
  }

  public static FindNodeMessage createFindNode(Bytes requestId) {
    return new FindNodeMessage(requestId, DEFAULT_DISTANCE);
  }
}
