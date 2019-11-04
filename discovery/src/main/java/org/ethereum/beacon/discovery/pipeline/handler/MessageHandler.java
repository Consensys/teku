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
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.processor.DiscoveryV5MessageProcessor;
import org.ethereum.beacon.discovery.processor.MessageProcessor;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class MessageHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(MessageHandler.class);
  private final MessageProcessor messageProcessor;

  public MessageHandler(NodeRecordFactory nodeRecordFactory) {
    this.messageProcessor =
        new MessageProcessor(new DiscoveryV5MessageProcessor(nodeRecordFactory));
    ;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessageHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessageHandler, requirements are satisfied!", envelope.getId()));

    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    DiscoveryMessage message = (DiscoveryMessage) envelope.get(Field.MESSAGE);
    try {
      messageProcessor.handleIncoming(message, session);
    } catch (Exception ex) {
      logger.trace(
          () ->
              String.format(
                  "Failed to handle message %s in envelope #%s", message, envelope.getId()),
          ex);
      envelope.put(Field.BAD_MESSAGE, message);
      envelope.put(Field.BAD_EXCEPTION, ex);
      envelope.remove(Field.MESSAGE);
    }
  }
}
