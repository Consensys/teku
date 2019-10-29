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
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import tech.pegasys.artemis.util.bytes.Bytes32;

/**
 * Tries to get WHOAREYOU packet from unknown incoming packet in {@link Field#PACKET_UNKNOWN}. If it
 * was successful, places the result in {@link Field#PACKET_WHOAREYOU}
 */
public class WhoAreYouAttempt implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouAttempt.class);
  private final Bytes32 homeNodeId;

  public WhoAreYouAttempt(Bytes32 homeNodeId) {
    this.homeNodeId = homeNodeId;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouAttempt, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_UNKNOWN, envelope)) {
      return;
    }
    if (!(HandlerUtil.requireCondition(
        envelope1 ->
            ((UnknownPacket) envelope1.get(Field.PACKET_UNKNOWN)).isWhoAreYouPacket(homeNodeId),
        envelope))) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouAttempt, requirements are satisfied!", envelope.getId()));

    UnknownPacket unknownPacket = (UnknownPacket) envelope.get(Field.PACKET_UNKNOWN);
    envelope.put(Field.PACKET_WHOAREYOU, unknownPacket.getWhoAreYouPacket());
    envelope.remove(Field.PACKET_UNKNOWN);
  }
}
