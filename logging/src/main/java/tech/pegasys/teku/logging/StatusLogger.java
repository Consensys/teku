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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.logging.ColorConsolePrinter.Color;

public class StatusLogger {

  private static final StatusLogger INSTANCE =
      new StatusLogger(ConsoleLoggingConfiguration.LOGGER_NAME);

  public static StatusLogger getLogger() {
    return INSTANCE;
  }

  private final Logger logger;

  private StatusLogger(String className) {
    this.logger = LogManager.getLogger(className);
  }

  public void log(Level level, String message) {
    this.logger.log(level, message);
  }

  public void epochEvent() {
    log(Level.INFO, "******* Epoch Event *******", Color.BLUE);
  }

  public void slotEvent() {
    log(Level.INFO, "******* Slot Event *******", Color.WHITE);
  }

  public void unprocessedAttestation(final Bytes32 beconBlockRoot) {
    log(
        Level.INFO,
        "New Attestation with block root:  " + beconBlockRoot + " detected.",
        Color.GREEN);
  }

  public void aggregateAndProof(final Bytes32 beconBlockRoot) {
    log(
        Level.INFO,
        "New AggregateAndProof with block root:  " + beconBlockRoot + " detected.",
        Color.BLUE);
  }

  public void unprocessedBlock(final Bytes32 stateRoot) {
    log(
        Level.INFO,
        "New BeaconBlock with state root:  " + stateRoot.toHexString() + " detected.",
        Color.GREEN);
  }

  // TODO only add colour when it is enabled vai the config
  private void log(Level level, String message, Color color) {
    this.logger.log(level, print(message, color));
  }
}
