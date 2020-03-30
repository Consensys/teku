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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.nio.file.Path;

public class RocksDbConfiguration {
  public static final int DEFAULT_MAX_OPEN_FILES = 1024;
  public static final int DEFAULT_MAX_BACKGROUND_COMPACTIONS = 4;
  public static final int DEFAULT_BACKGROUND_THREAD_COUNT = 4;
  public static final long DEFAULT_CACHE_CAPACITY = 8388608;

  private final Path databaseDir;
  private final int maxOpenFiles;
  private final int maxBackgroundCompactions;
  private final int backgroundThreadCount;
  private final long cacheCapacity;

  private RocksDbConfiguration(
      final Path databaseDir,
      final int maxOpenFiles,
      final int maxBackgroundCompactions,
      final int backgroundThreadCount,
      final long cacheCapacity) {
    this.maxOpenFiles = maxOpenFiles;
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    this.backgroundThreadCount = backgroundThreadCount;
    this.databaseDir = databaseDir;
    this.cacheCapacity = cacheCapacity;
  }

  public static RocksDbConfiguration withDataDirectory(final Path path) {
    return builder().databaseDir(path).build();
  }

  public static Builder builder() {
    return new Builder();
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

  public static class Builder {
    private int maxOpenFiles = DEFAULT_MAX_OPEN_FILES;
    private int maxBackgroundCompactions = DEFAULT_MAX_BACKGROUND_COMPACTIONS;
    private int backgroundThreadCount = DEFAULT_BACKGROUND_THREAD_COUNT;
    private long cacheCapacity = DEFAULT_CACHE_CAPACITY;

    private Path databaseDir;

    public RocksDbConfiguration build() {
      validate();
      return new RocksDbConfiguration(
          databaseDir,
          maxOpenFiles,
          maxBackgroundCompactions,
          backgroundThreadCount,
          cacheCapacity);
    }

    public Builder maxOpenFiles(final int maxOpenFiles) {
      this.maxOpenFiles = maxOpenFiles;
      return this;
    }

    public Builder maxBackgroundCompactions(final int maxBackgroundCompactions) {
      this.maxBackgroundCompactions = maxBackgroundCompactions;
      return this;
    }

    public Builder backgroundThreadCount(final int backgroundThreadCount) {
      this.backgroundThreadCount = backgroundThreadCount;
      return this;
    }

    public Builder cacheCapacity(final long cacheCapacity) {
      this.cacheCapacity = cacheCapacity;
      return this;
    }

    public Builder databaseDir(final Path databaseDir) {
      checkNotNull(databaseDir);
      this.databaseDir = databaseDir;
      return this;
    }

    private void validate() throws IllegalStateException {
      checkState(databaseDir != null, "Must supply a database directory");
    }
  }
}
