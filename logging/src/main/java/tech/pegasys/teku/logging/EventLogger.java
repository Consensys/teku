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
            shortenHash(finalizedRoot.toHexString()));
    info(epochEventLog, Color.YELLOW);
  }

  public void syncEvent(
      final UnsignedLong nodeSlot, final UnsignedLong headSlot, final int numPeers) {
    final String syncEventLog =
        String.format(
            "Sync Event *** Current slot: %s, Head slot: %s, Connected peers: %d",
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
      blockRoot = shortenHash(bestBlockRoot.toHexString());
    }
    final String slotEventLog =
        String.format(
            "Slot Event *** Slot: %s, Block: %s, Epoch: %s, Finalized checkpoint: %s, Finalized root: %s, Peers: %d",
            nodeSlot.toString(),
            blockRoot,
            nodeEpoch.toString(),
            finalizedCheckpoint.toString(),
            shortenHash(finalizedRoot.toHexString()),
            numPeers);
    info(slotEventLog, Color.WHITE);
  }

  public void unprocessedAttestation(final Bytes32 beaconBlockRoot) {
    debug("New Attestation with block root:  " + beaconBlockRoot + " detected.", Color.GREEN);
  }

  public void aggregateAndProof(final Bytes32 beaconBlockRoot) {
    debug("New AggregateAndProof with block root:  " + beaconBlockRoot + " detected.", Color.BLUE);
  }

  public void unprocessedBlock(final Bytes32 stateRoot) {
    debug(
        "New BeaconBlock with state root:  " + stateRoot.toHexString() + " detected.", Color.GREEN);
  }

  private void debug(final String message, final Color color) {
    log.debug(print(message, color));
  }

  private void info(final String message, final Color color) {
    log.info(print(message, color));
  }

  private String shortenHash(final String hash) {
    return String.format(
        "%s..%s", hash.substring(0, 6), hash.substring(hash.length() - 4, hash.length()));
  }
}
