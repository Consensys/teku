/*
 * Copyright Consensys Software Inc., 2026
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

/** Concrete test class for ConcurrentMapCache that inherits all contract tests. */
public class ConcurrentMapCacheTest extends CacheContractTest {

  @Override
  protected <K, V> Cache<K, V> createCache(final int capacity) {
    return new ConcurrentMapCache<>();
  }

  @Override
  protected <K, V> Cache<K, V> createUnboundedCache() {
    return new ConcurrentMapCache<>();
  }

  @Override
  @Test
  void get_shouldEvictAnItemWhenCapacityIsReached() {
    // ConcurrentMapCache is unbounded — verify it grows instead of evicting
    final Cache<Integer, Integer> cache = createCache(16);
    for (int i = 0; i <= 16; i++) {
      cache.get(i, key -> key);
    }
    assertThat(cache.size()).isEqualTo(17);
  }

  @Override
  @Test
  void copy_shouldPreserveCapacityLimit() {
    // ConcurrentMapCache is unbounded — verify copy preserves all entries and grows freely
    final Cache<Integer, Integer> cache = createCache(16);
    for (int i = 0; i < 20; i++) {
      cache.get(i, key -> key);
    }
    final Cache<Integer, Integer> copy = cache.copy();
    assertThat(copy.size()).isEqualTo(20);

    copy.get(100, key -> key);
    assertThat(copy.size()).isEqualTo(21);
    assertThat(cache.size()).isEqualTo(20);
  }
}
