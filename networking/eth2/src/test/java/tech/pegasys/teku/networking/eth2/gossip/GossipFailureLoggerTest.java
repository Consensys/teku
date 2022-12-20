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

import io.libp2p.pubsub.MessageAlreadySeenException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class GossipFailureLoggerTest {

  public static final String ALREADY_SEEN_MESSAGE =
      "Failed to publish thingy(s) for slot 1 because the message has already been seen";
  public static final UInt64 SLOT = UInt64.ONE;
  public static final String NO_PEERS_MESSAGE = noPeersMessage(SLOT.intValue());

  public static final String GENERIC_FAILURE_MESSAGE = "Failed to publish thingy(s) for slot 1";

  private final GossipFailureLogger logger = new GossipFailureLogger("thingy");

  @Test
  void shouldLogAlreadySeenErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new MessageAlreadySeenException("Dupe")), SLOT);
      logCaptor.assertDebugLog(ALREADY_SEEN_MESSAGE);
    }
  }

  @Test
  void shouldLogFirstNoPeersErrorsAtWarningLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new NoPeersForOutboundMessageException("So Lonely")), SLOT);
      logCaptor.assertWarnLog(NO_PEERS_MESSAGE);
    }
  }

  @Test
  void shouldLogRepeatedNoPeersErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new NoPeersForOutboundMessageException("So Lonely")), SLOT);
      logCaptor.clearLogs();

      logger.logWithSuppression(
          new IllegalStateException(
              "Foo", new NoPeersForOutboundMessageException("Not a friend in the world")),
          SLOT);
      logCaptor.assertDebugLog(NO_PEERS_MESSAGE);
    }
  }

  @Test
  void shouldLogNoPeersErrorsWithDifferentSlotsAtWarnLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new NoPeersForOutboundMessageException("So Lonely")), SLOT);
      logCaptor.assertWarnLog(NO_PEERS_MESSAGE);

      logger.logWithSuppression(
          new IllegalStateException(
              "Foo", new NoPeersForOutboundMessageException("Not a friend in the world")),
          UInt64.valueOf(2));
      logCaptor.assertWarnLog(noPeersMessage(2));
    }
  }

  @Test
  void shouldLogNoPeersErrorsAtWarnLevelWhenSeparatedByADifferentException() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new NoPeersForOutboundMessageException("So Lonely")), SLOT);
      logCaptor.assertWarnLog(NO_PEERS_MESSAGE);
      logCaptor.clearLogs();

      logger.logWithSuppression(new MessageAlreadySeenException("Dupe"), SLOT);

      logger.logWithSuppression(
          new IllegalStateException(
              "Foo", new NoPeersForOutboundMessageException("Not a friend in the world")),
          SLOT);
      logCaptor.assertWarnLog(NO_PEERS_MESSAGE);
    }
  }

  @Test
  void shouldLogFirstGenericErrorAtErrorLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.assertErrorLog(GENERIC_FAILURE_MESSAGE);
    }
  }

  @Test
  void shouldLogRepeatedGenericErrorsAtDebugLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.clearLogs();

      logger.logWithSuppression(
          new IllegalStateException("Foo", new IllegalStateException("goes the dynamite")), SLOT);
      logCaptor.assertDebugLog(GENERIC_FAILURE_MESSAGE);
    }
  }

  @Test
  void shouldLogMultipleGenericErrorsWithDifferentCausesAtErrorLevel() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      logger.logWithSuppression(
          new RuntimeException("Foo", new IllegalStateException("Boom")), SLOT);
      logCaptor.assertErrorLog(GENERIC_FAILURE_MESSAGE);
      logCaptor.clearLogs();

      logger.logWithSuppression(
          new IllegalStateException("Foo", new IllegalArgumentException("goes the dynamite")),
          SLOT);
      logCaptor.assertErrorLog(GENERIC_FAILURE_MESSAGE);
    }
  }

  private static String noPeersMessage(final int slot) {
    return "Failed to publish thingy(s) for slot "
        + slot
        + " because no peers were available on the required gossip topic";
  }
}
