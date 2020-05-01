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
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch,
      final Bytes32 finalizedRoot) {
    final String epochEventLog =
        String.format(
            "Epoch Event *** Current epoch: %s, Justified epoch: %s, Finalized epoch: %s, Finalized root: %s",
            currentEpoch.toString(),
            justifiedEpoch.toString(),
            finalizedEpoch.toString(),
            shortenHash(finalizedRoot.toHexString()));
    info(epochEventLog, Color.YELLOW);
  }

  public void syncEvent(
      final UnsignedLong nodeSlot, final UnsignedLong bestSlot, final int numPeers) {
    final String syncEventLog =
        String.format(
            "Sync Event *** Current slot: %s, Head block: %s, Connected peers: %d",
            nodeSlot, bestSlot.toString(), numPeers);
    info(syncEventLog, Color.WHITE);
  }

  public void slotEvent(
      final UnsignedLong nodeSlot,
      final UnsignedLong bestBlock,
      final Bytes32 bestBlockRoot,
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch,
      final Bytes32 finalizedRoot) {
    String blockRoot = " x ... empty";
    if (nodeSlot.equals(bestBlock)) {
      blockRoot = shortenHash(bestBlockRoot.toHexString());
    }
    final String slotEventLog =
        String.format(
            "Slot Event *** Slot: %s, Block: %s, Epoch: %s, Finalized Epoch: %s, Finalized Root: %s",
            nodeSlot.toString(),
            blockRoot,
            justifiedEpoch.toString(),
            finalizedEpoch.toString(),
            shortenHash(finalizedRoot.toHexString()));
    info(slotEventLog, Color.WHITE);
  }

  public void unprocessedAttestation(final Bytes32 beaconBlockRoot) {
    info("New Attestation with block root:  " + beaconBlockRoot + " detected.", Color.GREEN);
  }

  public void aggregateAndProof(final Bytes32 beaconBlockRoot) {
    info("New AggregateAndProof with block root:  " + beaconBlockRoot + " detected.", Color.BLUE);
  }

  public void unprocessedBlock(final Bytes32 stateRoot) {
    info(
        "New BeaconBlock with state root:  " + stateRoot.toHexString() + " detected.", Color.GREEN);
  }

  private void info(final String message, final Color color) {
    log.info(print(message, color));
  }

  private String shortenHash(final String hash) {
    return String.format(
        "%s..%s", hash.substring(0, 6), hash.substring(hash.length() - 4, hash.length()));
  }
}
