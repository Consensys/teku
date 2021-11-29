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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDB;
import tech.pegasys.leveldbjni.LevelDbJniLoader;

public enum DatabaseVersion {
  NOOP("noop"),
  V4("4"),
  V5("5"),
  V6("6"),
  LEVELDB1("leveldb1"),
  LEVELDB2("leveldb2"),
  LEVELDB_TREE("leveldb-tree");

  private static final Logger LOG = LogManager.getLogger();
  public static final DatabaseVersion DEFAULT_VERSION;
  private String value;

  static {
    if (isLevelDbSupported()) {
      DEFAULT_VERSION = LEVELDB_TREE;
    } else {
      DEFAULT_VERSION = V5;
    }
  }

  public static boolean isLevelDbSupported() {
    // Use JNI to load as the native library is loaded in a static block
    try {
      LevelDbJniLoader.loadNativeLibrary();
      return true;
    } catch (final UnsatisfiedLinkError e) {
      LOG.info("LevelDB not supported on this system: {}", e.getMessage());
      return false;
    } catch (final Throwable e) {
      LOG.error("Failed to check LevelDB support. Defaulting to RocksDB.", e);
      return false;
    }
  }

  public static boolean isRocksDbSupported() {
    try {
      RocksDB.loadLibrary();
      return true;
    } catch (final UnsatisfiedLinkError e) {
      return false;
    } catch (final Throwable t) {
      LOG.error("Failed to check RocksDB support.", t);
      return false;
    }
  }

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
