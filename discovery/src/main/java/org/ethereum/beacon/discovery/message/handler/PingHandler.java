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

package org.ethereum.beacon.discovery.message.handler;

import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import tech.pegasys.artemis.util.bytes.Bytes4;

public class PingHandler implements MessageHandler<PingMessage> {
  @Override
  public void handle(PingMessage message, NodeSession session) {
    PongMessage responseMessage =
        new PongMessage(
            message.getRequestId(),
            session.getNodeRecord().getSeq(),
            ((Bytes4) session.getNodeRecord().get(NodeRecord.FIELD_IP_V4)),
            (int) session.getNodeRecord().get(NodeRecord.FIELD_UDP_V4));
    session.sendOutgoing(
        MessagePacket.create(
            session.getHomeNodeId(),
            session.getNodeRecord().getNodeId(),
            session.getAuthTag().get(),
            session.getInitiatorKey(),
            DiscoveryV5Message.from(responseMessage)));
  }
}
