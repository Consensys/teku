/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class EventLogger {

  public static final EventLogger EVENT_LOG =
      new EventLogger(LoggingConfigurator.EVENT_LOGGER_NAME);

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private EventLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void genesisEvent(
      final Bytes32 hashTreeRoot, final Bytes32 genesisBlockRoot, final UInt64 genesisTime) {
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final String formattedGenesisTime =
        Instant.ofEpochSecond(genesisTime.longValue()).atZone(ZoneId.of("GMT")).format(formatter);

    final String genesisEventLog =
        String.format(
            "Genesis Event *** \n"
                + "Genesis state root: %s \n"
                + "Genesis block root: %s \n"
                + "Genesis time: %s GMT",
            hashTreeRoot.toHexString(), genesisBlockRoot.toHexString(), formattedGenesisTime);
    info(genesisEventLog, Color.CYAN);
  }

  public void epochEvent(
      final UInt64 currentEpoch,
      final UInt64 justifiedCheckpoint,
      final UInt64 finalizedCheckpoint,
      final Bytes32 finalizedRoot) {
    final String epochEventLog =
        String.format(
            "Epoch Event *** Epoch: %s, Justified checkpoint: %s, Finalized checkpoint: %s, Finalized root: %s",
            currentEpoch.toString(),
            justifiedCheckpoint.toString(),
            finalizedCheckpoint.toString(),
            LogFormatter.formatHashRoot(finalizedRoot));
    info(epochEventLog, Color.GREEN);
  }

  public void nodeSlotsMissed(final UInt64 oldSlot, final UInt64 newSlot) {
    final String driftEventLog =
        String.format(
            "Miss slots  *** Current slot: %s, previous slot: %s",
            newSlot.toString(), oldSlot.toString());
    info(driftEventLog, Color.WHITE);
  }

  public void syncEvent(final UInt64 nodeSlot, final UInt64 headSlot, final int numPeers) {
    final String syncEventLog =
        String.format(
            "Syncing     *** Target slot: %s, Head slot: %s, Remaining slots: %s, Connected peers: %s",
            nodeSlot, headSlot, nodeSlot.minusMinZero(headSlot), numPeers);
    info(syncEventLog, Color.WHITE);
  }

  public void syncCompleted() {
    info("Syncing completed", Color.GREEN);
  }

  public void syncStart() {
    info("Syncing started", Color.YELLOW);
  }

  public void weakSubjectivityFailedEvent(final Bytes32 blockRoot, final UInt64 slot) {
    final String weakSubjectivityFailedEventLog =
        String.format(
            "Syncing     *** Weak subjectivity check failed block: %s, slot: %s",
            LogFormatter.formatHashRoot(blockRoot), slot.toString());
    warn(weakSubjectivityFailedEventLog, Color.RED);
  }

  public void slotEvent(
      final UInt64 nodeSlot,
      final UInt64 headSlot,
      final Bytes32 bestBlockRoot,
      final UInt64 nodeEpoch,
      final UInt64 finalizedCheckpoint,
      final Bytes32 finalizedRoot,
      final int numPeers) {
    String blockRoot = "   ... empty";
    if (nodeSlot.equals(headSlot)) {
      blockRoot = LogFormatter.formatHashRoot(bestBlockRoot);
    }
    final String slotEventLog =
        String.format(
            "Slot Event  *** Slot: %s, Block: %s, Epoch: %s, Finalized checkpoint: %s, Finalized root: %s, Peers: %d",
            nodeSlot,
            blockRoot,
            nodeEpoch,
            finalizedCheckpoint,
            LogFormatter.formatHashRoot(finalizedRoot),
            numPeers);
    info(slotEventLog, Color.WHITE);
  }

  public void reorgEvent(
      final Bytes32 previousHeadRoot,
      final UInt64 previousHeadSlot,
      final Bytes32 newHeadRoot,
      final UInt64 newHeadSlot,
      final Bytes32 commonAncestorRoot,
      final UInt64 commonAncestorSlot) {
    String reorgEventLog =
        String.format(
            "Reorg Event *** New Head: %s, Previous Head: %s, Common Ancestor: %s",
            LogFormatter.formatBlock(newHeadSlot, newHeadRoot),
            LogFormatter.formatBlock(previousHeadSlot, previousHeadRoot),
            LogFormatter.formatBlock(commonAncestorSlot, commonAncestorRoot));
    info(reorgEventLog, Color.YELLOW);
  }

  public void networkUpgradeActivated(final UInt64 nodeEpoch, final String upgradeName) {
    info(
        String.format(
            "Milestone   *** Epoch: %s, Activating network upgrade: %s", nodeEpoch, upgradeName),
        Color.GREEN);
  }

  public void terminalPowBlockDetected(final Bytes32 terminalBlockHash) {
    info(
        String.format(
            "Merge       *** Terminal Block detected: %s",
            LogFormatter.formatHashRoot(terminalBlockHash)),
        Color.GREEN);
  }

  private void info(final String message, final Color color) {
    log.info(print(message, color));
  }

  private void warn(final String message, final Color color) {
    log.warn(print(message, color));
  }
}
