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

  private final Logger log;

  // TODO implement these using a supplier pattern to avoid unnecessary String creation when
  // disabled

  private StatusLogger(final String name) {
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

  // TODO UI type event (not really a Status update)
  public void sendDepositException(final Throwable t) {
    fatal("Failed to send deposit transaction: " + t.getClass() + " : " + t.getMessage());
  }

  // TODO UI type event (not really a Status update)
  public void generatingMockGenesis(final int validatorCount, final long genesisTime) {
    log.info(
        "Generating mock genesis state for {} validators at genesis time {}",
        validatorCount,
        genesisTime);
  }

  // TODO UI type event (not really a Status update)
  public void storingGenesis(final String outputFile, final boolean isComplete) {
    if (isComplete) {
      log.info("Genesis state file saved: {}", outputFile);
    } else {
      log.info("Saving genesis state to file: {}", outputFile);
    }
  }

  // TODO UI type event (not really a Status update)
  public void specificationFailure(final String description, final Throwable exception) {
    log.warn("Spec failed for {}: {}", description, exception, exception);
  }

  // TODO UI type event (not really a Status update)
  public void unexpectedException(final String description, final Throwable exception) {
    log.fatal(
        "PLEASE FIX OR REPORT | Unexpected exception thrown for {}: {}",
        exception,
        description,
        exception);
  }

  // TODO only add colour when it is enabled vai the config
  private void info(String message, Color color) {
    log.info(print(message, color));
  }

  // TODO only add colour when it is enabled vai the config
  private void fatal(String message) {
    log.fatal(print(message, Color.RED));
  }
}
