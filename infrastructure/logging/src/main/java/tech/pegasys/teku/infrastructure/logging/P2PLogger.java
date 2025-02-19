/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class P2PLogger {
  public static final P2PLogger P2P_LOG = new P2PLogger(LoggingConfigurator.P2P_LOGGER_NAME);

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private final boolean isIncludeP2pWarnings = LoggingConfigurator.isIncludeP2pWarnings();

  public P2PLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void onGossipMessageDecodingError(
      final String topic, final Bytes originalMessage, final Throwable error) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Failed to decode gossip message on topic {}, raw message: {}",
          topic,
          originalMessage,
          error);
    }
  }

  public void onGossipRejected(
      final String topic, final Bytes decodedMessage, final Optional<String> description) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting gossip message on topic {}, reason: {}, decoded message: {}",
          topic,
          description.orElse("failed validation"),
          decodedMessage);
    }
  }

  public void onInvalidBlock(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final Bytes blockSsz,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting invalid block at slot {} with root {} because {}. Full block data: {}",
          slot,
          blockRoot,
          failureReason,
          blockSsz,
          failureCause.orElse(null));
    }
  }

  public void onInvalidExecutionPayload(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final Bytes executionPayloadSsz,
      final String failureReason,
      final Optional<Throwable> failureCause) {
    if (isIncludeP2pWarnings) {
      log.warn(
          "Rejecting invalid execution payload at slot {} with root {} because {}. Full execution payload data: {}",
          slot,
          blockRoot,
          failureReason,
          executionPayloadSsz,
          failureCause.orElse(null));
    }
  }

  public record Peer(String peerId, String clientType) {}

  public void peersGossipScores(final Map<Peer, Double> scoresByPeerId) {
    final String header = String.format("%-54s %-11s %-10s", "Peer ID", "Client Type", "Score");
    final String separator =
        "------------------------------------------------------ ----------- ----------";

    final StringBuilder table = new StringBuilder();
    table
        .append("Total peers: ")
        .append(scoresByPeerId.size())
        .append(", Average score: ")
        .append(
            String.format(
                "%.2f", scoresByPeerId.values().stream().mapToDouble(i -> i).average().orElse(0)));
    table.append("\n").append(header).append("\n");
    table.append(separator).append("\n");

    for (final Map.Entry<Peer, Double> entry : scoresByPeerId.entrySet()) {
      final Peer peer = entry.getKey();
      final String row =
          String.format("%-54s %-11s %-10.2f", peer.peerId, peer.clientType, entry.getValue());
      table.append(row).append("\n");
    }

    log.info(ColorConsolePrinter.print(table.toString(), Color.PURPLE));
  }

  public void peersTopicSubscriptions(final Map<String, List<String>> topicSubscriptionsByPeerId) {
    final String header = String.format("%-71s %-54s", "Topic", "Peer ID(s)");
    final String separator =
        "----------------------------------------------------------------------- ------------------------------------------------------";

    final StringBuilder table = new StringBuilder();
    table.append("Total topics peers subscribed to: ").append(topicSubscriptionsByPeerId.size());
    table.append("\n").append(header).append("\n");
    table.append(separator).append("\n");

    for (final Map.Entry<String, List<String>> entry : topicSubscriptionsByPeerId.entrySet()) {
      final List<String> peerIds = entry.getValue();
      if (peerIds.isEmpty()) {
        final String row = String.format("%-71s %-54s", entry.getKey(), "");
        table.append(row).append("\n");
        continue;
      }
      final String firstRow = String.format("%-71s %-54s", entry.getKey(), peerIds.getFirst());
      table.append(firstRow).append("\n");
      for (int i = 1; i < peerIds.size(); i++) {
        final String row = String.format("%-71s %-54s", "", peerIds.get(i));
        table.append(row).append("\n");
      }
    }

    log.info(ColorConsolePrinter.print(table.toString(), Color.PURPLE));
  }
}
