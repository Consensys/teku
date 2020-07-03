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

package tech.pegasys.teku.storage.store;

import tech.pegasys.teku.util.config.Constants;

public class StorePruningOptions {
  public static final int DEFAULT_STATE_CACHE_SIZE = Constants.SLOTS_PER_EPOCH * 5;
  // Max block size is about 20x smaller than the minimum state size
  public static final int DEFAULT_BLOCK_CACHE_SIZE = DEFAULT_STATE_CACHE_SIZE * 10;
  public static final int DEFAULT_CHECKPOINT_STATE_CACHE_SIZE = 20;

  private final int stateCacheSize;
  private final int blockCacheSize;
  private final int checkpointStateCacheSize;

  private StorePruningOptions(
      final int stateCacheSize, final int blockCacheSize, final int checkpointStateCacheSize) {
    this.stateCacheSize = stateCacheSize;
    this.blockCacheSize = blockCacheSize;
    this.checkpointStateCacheSize = checkpointStateCacheSize;
  }

  public static StorePruningOptions createDefault() {
    return new StorePruningOptions(
        DEFAULT_STATE_CACHE_SIZE, DEFAULT_BLOCK_CACHE_SIZE, DEFAULT_CHECKPOINT_STATE_CACHE_SIZE);
  }

  public static StorePruningOptions create(
      final int stateCacheSize, final int blockCacheSize, final int checkpointStateCacheSize) {
    return new StorePruningOptions(stateCacheSize, blockCacheSize, checkpointStateCacheSize);
  }

  public int getStateCacheSize() {
    return stateCacheSize;
  }

  public int getBlockCacheSize() {
    return blockCacheSize;
  }

  public int getCheckpointStateCacheSize() {
    return checkpointStateCacheSize;
  }
}
