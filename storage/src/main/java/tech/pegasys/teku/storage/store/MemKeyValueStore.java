package tech.pegasys.teku.storage.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemKeyValueStore<K, V> implements KeyValueStore<K, V> {

  private final Map<K, V> store = new ConcurrentHashMap<>();

  @Override
  public void put(K key, V value) {
    store.put(key, value);
  }

  @Override
  public Optional<V> get(K key) {
    return Optional.ofNullable(store.get(key));
  }
}
