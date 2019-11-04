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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;

/**
 * Resolves session using `authTagRepo` for `WHOAREYOU` packets which should be placed in {@link
 * Field#PACKET_WHOAREYOU}
 */
public class WhoAreYouSessionResolver implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouSessionResolver.class);
  private final AuthTagRepository authTagRepo;

  public WhoAreYouSessionResolver(AuthTagRepository authTagRepo) {
    this.authTagRepo = authTagRepo;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouSessionResolver, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouSessionResolver, requirements are satisfied!",
                envelope.getId()));

    WhoAreYouPacket whoAreYouPacket = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    Optional<NodeSession> nodeSessionOptional = authTagRepo.get(whoAreYouPacket.getAuthTag());
    if (nodeSessionOptional.isPresent()
        && (nodeSessionOptional
                .get()
                .getStatus()
                .equals(
                    NodeSession.SessionStatus.RANDOM_PACKET_SENT) // We've started handshake before
            || nodeSessionOptional
                .get()
                .getStatus()
                .equals(
                    NodeSession.SessionStatus
                        .AUTHENTICATED))) { // We had authenticated session but it's expired
      envelope.put(Field.SESSION, nodeSessionOptional.get());
      logger.trace(
          () ->
              String.format(
                  "Session resolved: %s in envelope #%s",
                  nodeSessionOptional.get(), envelope.getId()));
    } else {
      envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_WHOAREYOU));
      envelope.remove(Field.PACKET_WHOAREYOU);
      envelope.put(Field.BAD_EXCEPTION, new RuntimeException("Not expected WHOAREYOU packet"));
    }
  }
}
