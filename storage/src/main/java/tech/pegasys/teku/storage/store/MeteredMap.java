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

package tech.pegasys.teku.storage.store;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;

/** Map extension with customized metrics. */
final class MeteredMap<K, V> implements Map<K, V> {
  final Map<K, V> delegate;
  final SettableLabelledGauge labelledGauge;
  final BiFunction<K, Optional<V>, Double> valueFunction;

  public MeteredMap(
      final Map<K, V> delegate,
      final SettableLabelledGauge labelledGauge,
      final BiFunction<K, Optional<V>, Double> valueFunction) {
    this.delegate = delegate;
    this.labelledGauge = labelledGauge;
    this.valueFunction = valueFunction;
  }

  @Override
  public V getOrDefault(final Object key, final V defaultValue) {
    final V maybeValue = get(key);
    if (maybeValue == null) {
      return defaultValue;
    } else {
      return maybeValue;
    }
  }

  @Override
  public void forEach(final BiConsumer<? super K, ? super V> action) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public V putIfAbsent(final K key, final V value) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean remove(final Object key, final Object value) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean replace(final K key, final V oldValue, final V newValue) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public V replace(final K key, final V value) {
    final V oldValue = delegate.replace(key, value);
    if (oldValue != null) {
      labelledGauge.set(valueFunction.apply((K) key, Optional.of(value)), "replace", "true");
    } else {
      labelledGauge.set(valueFunction.apply((K) key, Optional.empty()), "replace", "false");
    }
    return oldValue;
  }

  @Override
  public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public V computeIfPresent(
      final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public V compute(
      final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public V merge(
      final K key,
      final V value,
      final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean containsKey(final Object key) {
    final boolean containsKey = delegate.containsKey(key);
    if (containsKey) {
      labelledGauge.set(valueFunction.apply((K) key, Optional.empty()), "contains", "true");
    } else {
      labelledGauge.set(valueFunction.apply((K) key, Optional.empty()), "contains", "false");
    }
    return containsKey;
  }

  @Override
  public boolean containsValue(final Object value) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(final Object key) {
    final V maybeValue = delegate.get(key);
    if (maybeValue != null) {
      labelledGauge.set(valueFunction.apply((K) key, Optional.of(maybeValue)), "get", "true");
    } else {
      labelledGauge.set(valueFunction.apply((K) key, Optional.empty()), "get", "false");
    }
    return maybeValue;
  }

  @Override
  public V put(final K key, final V value) {
    final V oldValue = delegate.put(key, value);
    if (oldValue != null) {
      labelledGauge.set(valueFunction.apply(key, Optional.of(value)), "put", "true");
    } else {
      labelledGauge.set(valueFunction.apply(key, Optional.empty()), "put", "false");
    }
    return oldValue;
  }

  @Override
  @SuppressWarnings("unchecked")
  public V remove(final Object key) {
    final V oldValue = delegate.remove(key);
    if (oldValue != null) {
      labelledGauge.set(valueFunction.apply((K) key, Optional.of(oldValue)), "remove", "true");
    } else {
      labelledGauge.set(valueFunction.apply((K) key, Optional.empty()), "remove", "false");
    }
    return oldValue;
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> m) {
    m.forEach(this::put);
  }

  @Override
  public void clear() {
    labelledGauge.set(1d, "clear", "true");
    delegate.clear();
  }

  @Override
  public Set<K> keySet() {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public Collection<V> values() {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new RuntimeException("Not implemented");
  }
}
