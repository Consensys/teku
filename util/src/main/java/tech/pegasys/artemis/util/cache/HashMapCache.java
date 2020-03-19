package tech.pegasys.artemis.util.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class HashMapCache<K, V> implements Cache<K, V> {

  private Map<K, V> holder;

  private HashMapCache(Map<K, V> holder) {
    this.holder = holder;
  }

  public HashMapCache() {
    this(new HashMap<>());
  }

  @Override
  public synchronized V get(K key, Function<K, V> fallback) {
    return holder.computeIfAbsent(key, fallback);
  }

  @Override
  public synchronized Optional<V> getCached(K key) {
    return Optional.ofNullable(holder.get(key));
  }

  @Override
  public synchronized Cache<K, V> copy() {
    return new HashMapCache<>(new HashMap<>(holder));
  }

  @Override
  public synchronized Cache<K, V> transfer() {
    HashMapCache<K, V> ret = new HashMapCache<>(holder);
    holder = new HashMap<>();
    return ret;
  }

  @Override
  public synchronized void clear() {
    holder.clear();
  }
}
