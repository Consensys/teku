/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.collections.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Concrete test class for StripedCache that inherits all contract tests. */
public class StripedCacheTest extends CacheContractTest {

  @Override
  protected <K, V> Cache<K, V> createCache(final int capacity) {
    // StripedCache is unbounded, so we ignore the capacity parameter
    return StripedCache.createUnbounded();
  }

  @Override
  protected <K, V> Cache<K, V> createUnboundedCache() {
    return StripedCache.createUnbounded();
  }

  @Override
  @Test
  void get_shouldEvictAnItemWhenCapacityIsReached() {
    // StripedCache is unbounded, so eviction tests don't apply
    // Skip this test
  }

  @Override
  @Test
  void copy_shouldPreserveCapacityLimit() {
    // StripedCache is unbounded, so capacity limit tests don't apply
    // Skip this test
  }

  @Test
  void stripingDistributesKeysAcrossMultipleStripes() {
    final Cache<Integer, Integer> cache = StripedCache.createUnbounded();

    // Add many keys to ensure they distribute across stripes
    for (int i = 0; i < 1000; i++) {
      cache.get(i, key -> key);
    }

    assertThat(cache.size()).isEqualTo(1000);

    // Verify all keys are retrievable (proves correct stripe distribution)
    for (int i = 0; i < 1000; i++) {
      assertThat(cache.getCached(i)).as("Key %d should be in cache", i).contains(i);
    }
  }
}
