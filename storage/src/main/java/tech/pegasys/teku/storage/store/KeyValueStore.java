package tech.pegasys.teku.storage.store;

import java.util.Optional;

public interface KeyValueStore<K, V> {

  void put(K key, V value);

  Optional<V> get(K key);

  default void putAll(Iterable<KeyValue<? extends K,? extends V>> data) {
    data.forEach(kv -> put(kv.getKey(), kv.getValue()));
  }

  class KeyValue<K, V> {
    private final K key;
    private final V value;

    public KeyValue(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }
  }
}
