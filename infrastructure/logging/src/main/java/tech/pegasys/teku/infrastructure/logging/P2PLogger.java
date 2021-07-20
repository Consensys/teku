/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.logging;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class P2PLogger {
  public static final P2PLogger P2P_LOG = new P2PLogger(LoggingConfigurator.P2P_LOGGER_NAME);

  private final Logger log;

  public P2PLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void onGossipMessageDecodingError(
      final String topic, final Bytes originalMessage, final Throwable error) {
    log.warn(
        "Failed to decode gossip message on topic {}, raw message: {}",
        topic,
        originalMessage,
        error);
  }

  public void onGossipRejected(
      final String topic, final Bytes decodedMessage, final Optional<String> description) {
    log.warn(
        "Rejecting gossip message on topic {}, reason: {}, decoded message: {}",
        topic,
        description.orElse("failed validation"),
        decodedMessage);
  }
}
