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

import static tech.pegasys.teku.logging.LoggingConfigurator.isColorEnabled;

public class ColorConsolePrinter {

  private static final String resetCode = "\u001B[0m";

  public enum Color {
    RED,
    BLUE,
    PURPLE,
    WHITE,
    GREEN
  }

  public static String print(final String message, final Color color) {
    return isColorEnabled() ? colorCode(color) + message + resetCode : message;
  }

  private static String colorCode(final Color color) {
    switch (color) {
      case RED:
        return "\u001B[31m";
      case BLUE:
        return "\u001b[34;1m";
      case PURPLE:
        return "\u001B[35m";
      case WHITE:
        return "\033[1;30m";
      case GREEN:
        return "\u001B[32m";
      default:
        return "";
    }
  }
}
