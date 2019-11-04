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

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;

/**
 * Resolves incoming packet type based on session states and places packet into the corresponding
 * field. Doesn't recognize WhoAreYou packet, last should be resolved by {@link WhoAreYouAttempt}
 */
public class UnknownPacketTypeByStatus implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(UnknownPacketTypeByStatus.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTypeByStatus, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET_UNKNOWN, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTypeByStatus, requirements are satisfied!",
                envelope.getId()));

    UnknownPacket unknownPacket = (UnknownPacket) envelope.get(Field.PACKET_UNKNOWN);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    switch (session.getStatus()) {
      case INITIAL:
        {
          // We still don't know what's the type of the packet
          break;
        }
      case RANDOM_PACKET_SENT:
        {
          // Should receive WHOAREYOU in answer, not our case
          break;
        }
      case WHOAREYOU_SENT:
        {
          AuthHeaderMessagePacket authHeaderMessagePacket =
              unknownPacket.getAuthHeaderMessagePacket();
          envelope.put(Field.PACKET_AUTH_HEADER_MESSAGE, authHeaderMessagePacket);
          envelope.remove(Field.PACKET_UNKNOWN);
          break;
        }
      case AUTHENTICATED:
        {
          MessagePacket messagePacket = unknownPacket.getMessagePacket();
          envelope.put(Field.PACKET_MESSAGE, messagePacket);
          envelope.remove(Field.PACKET_UNKNOWN);
          break;
        }
      default:
        {
          String error =
              String.format(
                  "Not expected status:%s from node: %s",
                  session.getStatus(), session.getNodeRecord());
          logger.error(error);
          throw new RuntimeException(error);
        }
    }
  }
}
