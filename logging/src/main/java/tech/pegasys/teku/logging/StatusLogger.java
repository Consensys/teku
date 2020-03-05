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

public class StatusLogger {

  private static final StatusLogger INSTANCE =
      new StatusLogger(ConsoleLoggingConfiguration.LOGGER_NAME);

  public static StatusLogger getLogger() {
    return INSTANCE;
  }

  public enum Color {
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

  public void log(Level level, String message, boolean printEnabled) {
    if (printEnabled) {
      this.logger.log(level, message);
    }
  }

  public void log(Level level, String message, boolean printEnabled, Color color) {
    log(level, addColor(message, color), printEnabled);
  }

  public void log(Level level, String message, Color color) {
    this.logger.log(level, addColor(message, color));
  }

  public void log(Level level, String message, Throwable throwable) {
    this.logger.log(level, message, throwable);
  }

  public void log(Level level, String message, Throwable throwable, Color color) {
    this.logger.log(level, addColor(message, color), throwable);
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
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
