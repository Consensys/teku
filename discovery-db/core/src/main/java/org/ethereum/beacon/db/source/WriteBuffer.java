/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.db.source;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.ethereum.beacon.db.util.AutoCloseableLock;

/**
 * Accumulates changes made to underlying data source and flushes them upon a {@link #flush()} call.
 *
 * <p>This implementation is thread-safe and featured with cache size evaluator.
 *
 * <p>Created by Anton Nashatyrev on 12.11.2018.
 */
public class WriteBuffer<K, V> extends AbstractLinkedDataSource<K, V, K, V>
    implements CacheDataSource<K, V> {

  /** A buffer. */
  private final Map<K, CacheEntry<V>> buffer = new HashMap<>();

  /** A size evaluator. */
  private final CacheSizeEvaluator<K, V> sizeEvaluator;

  /** CRUD locks. */
  private final ReadWriteUpdateLock rwuLock = new ReentrantReadWriteUpdateLock();

  private final AutoCloseableLock readLock = AutoCloseableLock.wrap(rwuLock.readLock());
  private final AutoCloseableLock writeLock = AutoCloseableLock.wrap(rwuLock.writeLock());
  private final AutoCloseableLock updateLock = AutoCloseableLock.wrap(rwuLock.updateLock());

  public WriteBuffer(
      @Nonnull final DataSource<K, V> upstreamSource,
      @Nonnull final CacheSizeEvaluator<K, V> sizeEvaluator,
      final boolean upstreamFlush) {
    super(upstreamSource, upstreamFlush);
    Objects.requireNonNull(upstreamSource);
    Objects.requireNonNull(sizeEvaluator);

    this.sizeEvaluator = sizeEvaluator;
  }

  public WriteBuffer(@Nonnull final DataSource<K, V> upstreamSource, final boolean upstreamFlush) {
    this(upstreamSource, CacheSizeEvaluator.noSizeEvaluator(), upstreamFlush);
  }

  @Override
  public Optional<V> get(@Nonnull final K key) {
    Objects.requireNonNull(key);
    try (AutoCloseableLock l = readLock.lock()) {
      CacheEntry<V> entry = buffer.get(key);
      if (entry == null) {
        return getUpstream().get(key);
      } else if (entry == CacheEntry.REMOVED) {
        return Optional.empty();
      } else {
        return Optional.of(entry.value);
      }
    }
  }

  @Override
  public void put(@Nonnull final K key, @Nonnull final V value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    try (AutoCloseableLock l = writeLock.lock()) {
      buffer.put(key, CacheEntry.of(value));
      sizeEvaluator.added(key, value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void remove(@Nonnull final K key) {
    Objects.requireNonNull(key);
    try (AutoCloseableLock l = writeLock.lock()) {
      CacheEntry<V> entry = buffer.put(key, CacheEntry.REMOVED);
      if (entry != null && entry.getValue().isPresent()) {
        sizeEvaluator.removed(key, entry.getValue().get());
      }
    }
  }

  @Override
  public void doFlush() {
    try (AutoCloseableLock rl = updateLock.lock()) {
      if (getUpstream() instanceof BatchUpdateDataSource) {
        final Map<K, V> updates = new HashMap<>();
        buffer.forEach((key, value) -> updates.put(key, value.value));
        ((BatchUpdateDataSource<K, V>) getUpstream()).batchUpdate(updates);
      } else {
        buffer.forEach((key, value) -> getUpstream().put(key, value.value));
      }

      reset();
    }
  }

  /** Discards all changes accumulated */
  public void reset() {
    try (AutoCloseableLock l = writeLock.lock()) {
      buffer.clear();
      sizeEvaluator.reset();
    }
  }

  @Override
  public Optional<Optional<V>> getCacheEntry(@Nonnull final K key) {
    Objects.requireNonNull(key);

    try (AutoCloseableLock l = readLock.lock()) {
      CacheEntry<V> entry = buffer.get(key);
      return Optional.ofNullable(entry == null ? null : entry.getValue());
    }
  }

  @Override
  public long evaluateSize() {
    return sizeEvaluator.getEvaluatedSize();
  }

  /**
   * A structure holding cache entry.
   *
   * @param <V> a value type.
   */
  private static final class CacheEntry<V> {

    /**
     * Indicates removed value.
     *
     * <p><strong>Note:</strong> passing a {@code null} value to {@code REMOVED} entry to keep an
     * invariant for {@link BatchUpdateDataSource#batchUpdate(Map)}: {@code null} entries are meant
     * to be deleted by the upstream source.
     */
    private static final CacheEntry REMOVED = CacheEntry.of(null);

    private V value;

    private CacheEntry(@Nullable V value) {
      this.value = value;
    }

    private static <V> CacheEntry<V> of(@Nullable V value) {
      return new CacheEntry<>(value);
    }

    private Optional<V> getValue() {
      return Optional.ofNullable(value);
    }
  }
}
