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
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;

/** Handles {@link MessagePacket} in {@link Field#PACKET_MESSAGE} field */
public class MessagePacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(MessagePacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessagePacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessagePacketHandler, requirements are satisfied!",
                envelope.getId()));

    MessagePacket packet = (MessagePacket) envelope.get(Field.PACKET_MESSAGE);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);

    try {
      packet.decode(session.getRecipientKey());
      envelope.put(Field.MESSAGE, packet.getMessage());
    } catch (AssertionError ex) {
      logger.error(
          String.format(
              "Verification not passed for message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus()));
      envelope.remove(Field.PACKET_MESSAGE);
      envelope.put(Field.BAD_PACKET, packet);
      return;
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_MESSAGE);
      envelope.put(Field.BAD_PACKET, packet);
      return;
    }
    envelope.remove(Field.PACKET_MESSAGE);
  }
}
