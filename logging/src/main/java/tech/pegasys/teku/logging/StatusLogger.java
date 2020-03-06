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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

public class StatusLogger {

  private static final StatusLogger INSTANCE =
      new StatusLogger(ConsoleLoggingConfiguration.LOGGER_NAME);

  public static StatusLogger getLogger() {
    return INSTANCE;
  }

  private enum Color {
    RED,
    BLUE,
    PURPLE,
    WHITE,
    GREEN
  }

  private static final String resetCode = "\u001B[0m";

  private final Logger logger;

  protected StatusLogger(String className) {
    this.logger = LogManager.getLogger(className);
  }

  public void log(Level level, String message) {
    this.logger.log(level, message);
  }

  public void epochEvent() {
    log(Level.INFO, "******* Epoch Event *******", StatusLogger.Color.BLUE);
  }

  public void slotEvent() {
    log(Level.INFO, "******* Slot Event *******", StatusLogger.Color.WHITE);
  }

  public void unprocessedAttestation(final Bytes32 beconBlockRoot) {
    log(
        Level.INFO,
        "New Attestation with block root:  " + beconBlockRoot + " detected.",
        StatusLogger.Color.GREEN);
  }

  public void aggregateAndProof(final Bytes32 beconBlockRoot) {
    log(
        Level.INFO,
        "New AggregateAndProof with block root:  " + beconBlockRoot + " detected.",
        StatusLogger.Color.BLUE);
  }

  public void unprocessedBlock(final Bytes32 stateRoot) {
    log(
        Level.INFO,
        "New BeaconBlock with state root:  " + stateRoot.toHexString() + " detected.",
        StatusLogger.Color.GREEN);
  }

  // TODO only add colour when it is enabled vai the config
  private void log(Level level, String message, Color color) {
    this.logger.log(level, addColor(message, color));
  }

  private String findColor(Color color) {
    String colorCode = "";
    switch (color) {
      case RED:
        colorCode = "\u001B[31m";
        break;
      case BLUE:
        colorCode = "\u001b[34;1m";
        break;
      case PURPLE:
        colorCode = "\u001B[35m";
        break;
      case WHITE:
        colorCode = "\033[1;30m";
        break;
      case GREEN:
        colorCode = "\u001B[32m";
        break;
    }
    return colorCode;
  }

  private String addColor(String message, Color color) {
    String colorCode = findColor(color);
    return colorCode + message + resetCode;
  }
}
