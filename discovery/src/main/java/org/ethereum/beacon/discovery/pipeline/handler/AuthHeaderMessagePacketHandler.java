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

import static org.ethereum.beacon.discovery.schema.NodeSession.SessionStatus.AUTHENTICATED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link AuthHeaderMessagePacket} in {@link Field#PACKET_AUTH_HEADER_MESSAGE} field */
public class AuthHeaderMessagePacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(AuthHeaderMessagePacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;
  private final NodeRecordFactory nodeRecordFactory;

  public AuthHeaderMessagePacketHandler(
      Pipeline outgoingPipeline, Scheduler scheduler, NodeRecordFactory nodeRecordFactory) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
    this.nodeRecordFactory = nodeRecordFactory;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in AuthHeaderMessagePacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_AUTH_HEADER_MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in AuthHeaderMessagePacketHandler, requirements are satisfied!",
                envelope.getId()));

    AuthHeaderMessagePacket packet =
        (AuthHeaderMessagePacket) envelope.get(Field.PACKET_AUTH_HEADER_MESSAGE);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      packet.decodeEphemeralPubKey();
      Bytes ephemeralPubKey = packet.getEphemeralPubkey();
      Functions.HKDFKeys keys =
          Functions.hkdf_expand(
              session.getNodeRecord().getNodeId(),
              session.getHomeNodeId(),
              session.getStaticNodeKey(),
              ephemeralPubKey,
              session.getIdNonce());
      // Swap keys because we are not initiator, other side is
      session.setInitiatorKey(keys.getRecipientKey());
      session.setRecipientKey(keys.getInitiatorKey());
      packet.decodeMessage(session.getRecipientKey(), keys.getAuthResponseKey(), nodeRecordFactory);
      packet.verify(
          session.getIdNonce(), (Bytes) session.getNodeRecord().get(EnrFieldV4.PKEY_SECP256K1));
      envelope.put(Field.MESSAGE, packet.getMessage());
    } catch (AssertionError ex) {
      logger.info(
          String.format(
              "Verification not passed for message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus()));
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
      session.cancelAllRequests("Failed to handshake");
      return;
    }
    session.setStatus(AUTHENTICATED);
    envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
    NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
  }
}
