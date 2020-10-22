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

package tech.pegasys.teku.storage.server;

import java.util.Optional;

public enum DatabaseVersion {
  NOOP("noop"),
  V4("4"),
  V5("5"),
  V6("6");

  public static final DatabaseVersion DEFAULT_VERSION = DatabaseVersion.V5;
  private String value;

  DatabaseVersion(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static Optional<DatabaseVersion> fromString(final String value) {
    final String normalizedValue = value.trim();
    for (DatabaseVersion version : DatabaseVersion.values()) {
      if (version.getValue().equalsIgnoreCase(normalizedValue)) {
        return Optional.of(version);
      }
    }
    return Optional.empty();
  }
}
