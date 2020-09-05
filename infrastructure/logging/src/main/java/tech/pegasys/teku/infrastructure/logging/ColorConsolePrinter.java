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

public class ColorConsolePrinter {

  private static final String resetCode = "\u001B[0m";

  public enum Color {
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    PURPLE,
    CYAN,
    WHITE
  }

  public static String print(final String message, final Color color) {
    return LoggingConfigurator.isColorEnabled() ? colorCode(color) + message + resetCode : message;
  }

  private static String colorCode(final Color color) {
    switch (color) {
      case BLACK:
        return "\u001b[30m";
      case RED:
        return "\u001b[31m";
      case GREEN:
        return "\u001b[32m";
      case YELLOW:
        return "\u001b[33m";
      case BLUE:
        return "\u001b[34m";
      case PURPLE:
        return "\u001b[35m";
      case CYAN:
        return "\u001b[36m";
      case WHITE:
        return "\u001b[37m";
      default:
        return "";
    }
  }
}
