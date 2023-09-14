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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

/** Map extension with customized metrics. */
final class MeteredMap<K, V> implements Map<K, V> {
  final Map<K, V> delegate;
  final LabelledMetric<Counter> labelledCounter;
  final BiFunction<K, V, String> valueFunction;

  public MeteredMap(
      final Map<K, V> delegate,
      final LabelledMetric<Counter> labelledCounter,
      final BiFunction<K, V, String> valueFunction) {
    this.delegate = delegate;
    this.labelledCounter = labelledCounter;
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
    throw new RuntimeException("Not implemented");
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
  public boolean containsKey(final Object key) {
    final boolean containsKey = delegate.containsKey(key);
    if (containsKey) {
      labelledCounter.labels("contains", "true", "-1").inc();
    } else {
      labelledCounter.labels("contains", "false", "-1").inc();
    }
    return containsKey;
  }

  @Override
  public boolean containsValue(final Object value) {
    return delegate.containsValue(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(final Object key) {
    final V maybeValue = delegate.get(key);
    if (maybeValue != null) {
      labelledCounter.labels("get", "true", valueFunction.apply((K) key, maybeValue)).inc();
    }
    return maybeValue;
  }

  @Override
  public V put(final K key, final V value) {
    final V oldValue = delegate.put(key, value);
    labelledCounter
        .labels("put", String.valueOf(oldValue != null), valueFunction.apply(key, value))
        .inc();
    return oldValue;
  }

  @Override
  @SuppressWarnings("unchecked")
  public V remove(final Object key) {
    final V maybeValue = delegate.remove(key);
    if (maybeValue != null) {
      labelledCounter.labels("remove", "true", valueFunction.apply((K) key, maybeValue)).inc();
    }
    return maybeValue;
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> m) {
    m.forEach(this::put);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return delegate.entrySet();
  }
}
