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

package tech.pegasys.artemis.storage.rocksdb;

import java.nio.file.Path;

public class RocksDbConfiguration {

  private final int maxOpenFiles;
  private final int maxBackgroundCompactions;
  private final int backgroundThreadCount;
  private final Path databaseDir;
  private final long cacheCapacity;

  public RocksDbConfiguration(
      final int maxOpenFiles,
      final int maxBackgroundCompactions,
      final int backgroundThreadCount,
      final Path databaseDir,
      final long cacheCapacity) {
    this.maxOpenFiles = maxOpenFiles;
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    this.backgroundThreadCount = backgroundThreadCount;
    this.databaseDir = databaseDir;
    this.cacheCapacity = cacheCapacity;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public int getMaxBackgroundCompactions() {
    return maxBackgroundCompactions;
  }

  public int getBackgroundThreadCount() {
    return backgroundThreadCount;
  }

  public Path getDatabaseDir() {
    return databaseDir;
  }

  public long getCacheCapacity() {
    return cacheCapacity;
  }
}
