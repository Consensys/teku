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

import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.crypto.ECKeyPair;

/** Handles {@link WhoAreYouPacket} in {@link Field#PACKET_WHOAREYOU} field */
public class WhoAreYouPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouPacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;

  public WhoAreYouPacketHandler(Pipeline outgoingPipeline, Scheduler scheduler) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, requirements are satisfied!",
                envelope.getId()));

    WhoAreYouPacket packet = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      NodeRecord respRecord = null;
      if (packet.getEnrSeq().compareTo(session.getHomeNodeRecord().getSeq()) < 0) {
        respRecord = session.getHomeNodeRecord();
      }
      Bytes remotePubKey = (Bytes) session.getNodeRecord().getKey(EnrFieldV4.PKEY_SECP256K1);
      byte[] ephemeralKeyBytes = new byte[32];
      Functions.getRandom().nextBytes(ephemeralKeyBytes);
      ECKeyPair ephemeralKey = ECKeyPair.create(ephemeralKeyBytes);

      Functions.HKDFKeys hkdfKeys =
          Functions.hkdf_expand(
              session.getHomeNodeId(),
              session.getNodeRecord().getNodeId(),
              Bytes.wrap(ephemeralKeyBytes),
              remotePubKey,
              packet.getIdNonce());
      session.setInitiatorKey(hkdfKeys.getInitiatorKey());
      session.setRecipientKey(hkdfKeys.getRecipientKey());
      Bytes authResponseKey = hkdfKeys.getAuthResponseKey();
      Optional<RequestInfo> requestInfoOpt = session.getFirstAwaitRequestInfo();
      final V5Message message =
          requestInfoOpt
              .map(requestInfo -> TaskMessageFactory.createMessageFromRequest(requestInfo, session))
              .orElseThrow(
                  (Supplier<Throwable>)
                      () ->
                          new RuntimeException(
                              String.format(
                                  "Received WHOAREYOU in envelope #%s but no requests await in %s session",
                                  envelope.getId(), session)));

      Bytes ephemeralPubKey =
          Bytes.wrap(
              Utils.extractBytesFromUnsignedBigInt(ephemeralKey.getPublicKey(), PUBKEY_SIZE));
      AuthHeaderMessagePacket response =
          AuthHeaderMessagePacket.create(
              session.getHomeNodeId(),
              session.getNodeRecord().getNodeId(),
              authResponseKey,
              packet.getIdNonce(),
              session.getStaticNodeKey(),
              respRecord,
              ephemeralPubKey,
              session.generateNonce(),
              hkdfKeys.getInitiatorKey(),
              DiscoveryV5Message.from(message));
      session.sendOutgoing(response);
    } catch (AssertionError ex) {
      String error =
          String.format(
              "Verification not passed for message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
      return;
    } catch (Throwable ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
      return;
    }
    session.setStatus(NodeSession.SessionStatus.AUTHENTICATED);
    envelope.remove(Field.PACKET_WHOAREYOU);
    NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
  }
}
