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

package tech.pegasys.teku.logging;

import static tech.pegasys.teku.logging.ColorConsolePrinter.print;
import static tech.pegasys.teku.logging.LogFormatter.formatHashRoot;
import static tech.pegasys.teku.logging.LoggingConfigurator.EVENT_LOGGER_NAME;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.logging.ColorConsolePrinter.Color;

public class EventLogger {

  public static final EventLogger EVENT_LOG = new EventLogger(EVENT_LOGGER_NAME);

  private final Logger log;

  private EventLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void genesisEvent(final Bytes32 hashTreeRoot, final Bytes32 genesisBlockRoot) {
    final String genesisEventLog =
        String.format(
            "Genesis Event *** Initial state root: %s, Genesis block root: %s",
            hashTreeRoot.toHexString(), genesisBlockRoot.toHexString());
    info(genesisEventLog, Color.CYAN);
  }

  public void epochEvent(
      final UnsignedLong currentEpoch,
      final UnsignedLong justifiedCheckpoint,
      final UnsignedLong finalizedCheckpoint,
      final Bytes32 finalizedRoot) {
    final String epochEventLog =
        String.format(
            "Epoch Event *** Epoch: %s, Justified checkpoint: %s, Finalized checkpoint: %s, Finalized root: %s",
            currentEpoch.toString(),
            justifiedCheckpoint.toString(),
            finalizedCheckpoint.toString(),
            formatHashRoot(finalizedRoot));
    info(epochEventLog, Color.GREEN);
  }

  public void nodeSlotsMissed(final UnsignedLong oldSlot, final UnsignedLong newSlot) {
    final String driftEventLog =
        String.format(
            "Miss slots  *** Current slot: %s, previous slot: %s",
            newSlot.toString(), oldSlot.toString());
    info(driftEventLog, Color.WHITE);
  }

  public void syncEvent(
      final UnsignedLong nodeSlot, final UnsignedLong headSlot, final int numPeers) {
    final String syncEventLog =
        String.format(
            "Sync Event  *** Current slot: %s, Head slot: %s, Connected peers: %d",
            nodeSlot, headSlot.toString(), numPeers);
    info(syncEventLog, Color.WHITE);
  }

  public void slotEvent(
      final UnsignedLong nodeSlot,
      final UnsignedLong headSlot,
      final Bytes32 bestBlockRoot,
      final UnsignedLong nodeEpoch,
      final UnsignedLong finalizedCheckpoint,
      final Bytes32 finalizedRoot,
      final int numPeers) {
    String blockRoot = "   ... empty";
    if (nodeSlot.equals(headSlot)) {
      blockRoot = formatHashRoot(bestBlockRoot);
    }
    final String slotEventLog =
        String.format(
            "Slot Event  *** Slot: %s, Block: %s, Epoch: %s, Finalized checkpoint: %s, Finalized root: %s, Peers: %d",
            nodeSlot.toString(),
            blockRoot,
            nodeEpoch.toString(),
            finalizedCheckpoint.toString(),
            formatHashRoot(finalizedRoot),
            numPeers);
    info(slotEventLog, Color.WHITE);
  }

  private void info(final String message, final Color color) {
    log.info(print(message, color));
  }
}
