/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.base.Throwables;
import io.libp2p.pubsub.MessageAlreadySeenException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GossipFailureLogger {
  private static final Logger LOG = LogManager.getLogger();

  private final String messageType;
  private UInt64 lastErroredSlot;
  private Throwable lastRootCause;

  public GossipFailureLogger(final String messageType) {
    this.messageType = messageType;
  }

  public synchronized void logWithSuppression(final Throwable error, final UInt64 slot) {
    final Throwable rootCause = Throwables.getRootCause(error);

    final boolean suppress =
        slot.equals(lastErroredSlot) && rootCause.getClass().equals(lastRootCause.getClass());

    lastErroredSlot = slot;
    lastRootCause = rootCause;

    if (lastRootCause instanceof MessageAlreadySeenException) {
      LOG.debug(
          "Failed to publish {}(s) for slot {} because the message has already been seen",
          messageType,
          lastErroredSlot);
    } else if (lastRootCause instanceof NoPeersForOutboundMessageException) {
      LOG.log(
          suppress ? Level.DEBUG : Level.WARN,
          "Failed to publish {}(s) for slot {} because no peers were available on the required gossip topic",
          messageType,
          lastErroredSlot);
    } else {
      LOG.log(
          suppress ? Level.DEBUG : Level.ERROR,
          "Failed to publish {}(s) for slot {}",
          messageType,
          lastErroredSlot,
          error);
    }
  }
}
