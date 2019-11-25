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

package org.ethereum.beacon.discovery.processor;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.Protocol;

/**
 * Highest level processor which knows several processors for different versions of {@link
 * DiscoveryMessage}'s.
 */
public class MessageProcessor {
  private static final Logger logger = LogManager.getLogger(MessageProcessor.class);

  @SuppressWarnings({"rawtypes"})
  private final Map<Protocol, DiscoveryMessageProcessor> messageProcessors = new HashMap<>();

  @SuppressWarnings({"rawtypes"})
  public MessageProcessor(DiscoveryMessageProcessor... messageProcessors) {
    for (int i = 0; i < messageProcessors.length; ++i) {
      this.messageProcessors.put(messageProcessors[i].getSupportedIdentity(), messageProcessors[i]);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handleIncoming(DiscoveryMessage message, NodeSession session) {
    Protocol protocol = message.getProtocol();
    DiscoveryMessageProcessor messageHandler = messageProcessors.get(protocol);
    if (messageHandler == null) {
      String error =
          String.format(
              "Message %s with identity %s received in session %s is not supported",
              message, protocol, session);
      logger.error(error);
      throw new RuntimeException(error);
    }
    messageHandler.handleMessage(message, session);
  }
}
