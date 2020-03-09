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

package tech.pegasys.teku.logging;

import static tech.pegasys.teku.logging.ColorConsolePrinter.print;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.logging.ColorConsolePrinter.Color;

public class StatusLogger {

  public static final StatusLogger STATUS_LOG = new StatusLogger("stdout");

  private final Logger logger;
  private boolean enabled;

  private StatusLogger(final String name) {
    this.logger = LogManager.getLogger(name);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public void genesisEvent(final Bytes32 hashTreeRoot, final Bytes32 genesisBlockRoot) {
    info("******* Eth2Genesis Event*******", Color.WHITE);
    info("Initial state root is " + hashTreeRoot.toHexString());
    info("Genesis block root is " + genesisBlockRoot.toHexString());
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
    info("Node slot:                             " + nodeSlot);
    info("Head block slot:" + "                       " + bestSlot);
    info("Justified epoch:" + "                       " + justifiedEpoch);
    info("Finalized epoch:" + "                       " + finalizedEpoch);
  }

  public void unprocessedAttestation(final Bytes32 beconBlockRoot) {
    info("New Attestation with block root:  " + beconBlockRoot + " detected.", Color.GREEN);
  }

  public void aggregateAndProof(final Bytes32 beconBlockRoot) {
    info("New AggregateAndProof with block root:  " + beconBlockRoot + " detected.", Color.BLUE);
  }

  public void unprocessedBlock(final Bytes32 stateRoot) {
    info(
        "New BeaconBlock with state root:  " + stateRoot.toHexString() + " detected.", Color.GREEN);
  }

  // TODO only add colour when it is enabled vai the config
  private void info(String message, Color color) {
    if (enabled) {
      logger.info(print(message, color));
    }
  }

  private void info(String message) {
    if (enabled) {
      logger.info(message);
    }
  }
}
