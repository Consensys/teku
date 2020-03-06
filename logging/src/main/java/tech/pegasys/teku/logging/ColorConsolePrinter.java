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

public class ColorConsolePrinter {

  private static final String resetCode = "\u001B[0m";

  public enum Color {
    RED,
    BLUE,
    PURPLE,
    WHITE,
    GREEN
  }

  private static String findColor(Color color) {
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

  public static String print(final String message, final Color color) {
    String colorCode = findColor(color);
    return colorCode + message + resetCode;
  }
}
