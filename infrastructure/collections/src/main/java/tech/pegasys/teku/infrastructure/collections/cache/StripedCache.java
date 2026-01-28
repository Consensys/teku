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

import java.util.Optional;
import java.util.function.Function;

/**
 * Cache implementation that uses striping to reduce lock contention. Internally manages multiple
 * LRUCache instances (stripes) and distributes keys across them based on hash code.
 *
 * <p>This provides better concurrency than a single synchronized cache while maintaining the memory
 * efficiency and key deduplication properties of LinkedHashMap.
 *
 * <p>Use this for unbounded caches on hot paths where lock contention is a concern.
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class StripedCache<K, V> implements Cache<K, V> {

  private static final int STRIPE_COUNT = 16; // Power of 2 for efficient modulo
  private static final int STRIPE_MASK = STRIPE_COUNT - 1;

  private final LRUCache<K, V>[] stripes;

  @SuppressWarnings({"unchecked", "rawtypes"})
  private StripedCache(final int capacityPerStripe) {
    this.stripes = new LRUCache[STRIPE_COUNT];
    for (int i = 0; i < STRIPE_COUNT; i++) {
      this.stripes[i] = LRUCache.create(capacityPerStripe);
    }
  }

  /**
   * Creates an unbounded striped cache. Each stripe can grow without limit, so the total capacity
   * is effectively unbounded.
   *
   * @return A new instance of StripedCache.
   */
  public static <K, V> StripedCache<K, V> createUnbounded() {
    // Use Integer.MAX_VALUE for each stripe to make it effectively unbounded
    return new StripedCache<>(Integer.MAX_VALUE / STRIPE_COUNT);
  }

  /** Gets the stripe index for a given key based on its hash code. */
  private int getStripeIndex(final K key) {
    // Use bitwise AND with mask for efficient modulo on power of 2
    return (key.hashCode() & 0x7FFFFFFF) & STRIPE_MASK;
  }

  private LRUCache<K, V> getStripe(final K key) {
    return stripes[getStripeIndex(key)];
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    return getStripe(key).get(key, fallback);
  }

  @Override
  public Optional<V> getCached(final K key) {
    return getStripe(key).getCached(key);
  }

  @Override
  public void invalidate(final K key) {
    getStripe(key).invalidate(key);
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    getStripe(key).invalidateWithNewValue(key, newValue);
  }

  @Override
  public void clear() {
    for (LRUCache<K, V> stripe : stripes) {
      stripe.clear();
    }
  }

  @Override
  public int size() {
    int totalSize = 0;
    for (LRUCache<K, V> stripe : stripes) {
      totalSize += stripe.size();
    }
    return totalSize;
  }

  @Override
  public Cache<K, V> copy() {
    final StripedCache<K, V> newCache = new StripedCache<>(Integer.MAX_VALUE / STRIPE_COUNT);
    for (int i = 0; i < STRIPE_COUNT; i++) {
      newCache.stripes[i] = (LRUCache<K, V>) this.stripes[i].copy();
    }
    return newCache;
  }
}
