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

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.logging.ColorConsolePrinter.Color;

public class EventLogger {

  public static final EventLogger EVENT_LOG = new EventLogger("stdout");

  private final Logger log;

  private EventLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void genesisEvent(final Bytes32 hashTreeRoot, final Bytes32 genesisBlockRoot) {
    info("******* Eth2Genesis Event*******", Color.WHITE);
    log.info("Initial state root is {}", hashTreeRoot.toHexString());
    log.info("Genesis block root is {}", genesisBlockRoot.toHexString());
  }

  public void epochEvent() {
    info("******* Epoch Event *******", Color.PURPLE);
  }

  public void slotEvent(
      final UnsignedLong nodeSlot,
      final UnsignedLong bestSlot,
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch) {
    info("******* Slot Event *******", Color.WHITE);
    log.info("Node slot:                             {}", nodeSlot);
    log.info("Head block slot:                       {}", bestSlot);
    log.info("Justified epoch:                       {}", justifiedEpoch);
    log.info("Finalized epoch:                       {}", finalizedEpoch);
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

  // TODO only add colour when it is enabled vai the config
  private void info(String message, Color color) {
    log.info(print(message, color));
  }
}
