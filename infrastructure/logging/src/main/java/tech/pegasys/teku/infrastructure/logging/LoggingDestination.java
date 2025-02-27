/*
 * Copyright Consensys Software Inc., 2022
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum LoggingDestination {
  BOTH("both"),
  CONSOLE("console"),
  DEFAULT_BOTH("default"),
  FILE("file"),
  CUSTOM("custom");

  private static final Logger LOG = LogManager.getLogger();
  private final String key;

  LoggingDestination(final String key) {
    this.key = key;
  }

  public static LoggingDestination get(final String destination) {
    for (final LoggingDestination candidate : LoggingDestination.values()) {
      if (candidate.key.equalsIgnoreCase(destination)) {
        return candidate;
      }
    }

    LOG.warn(
        "Invalid logging destination '{}' provided. Valid options are: both, console, default, file, custom. Using default destination.",
        destination);
    return DEFAULT_BOTH;
  }
}
