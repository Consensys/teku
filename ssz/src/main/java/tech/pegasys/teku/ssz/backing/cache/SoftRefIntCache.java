package tech.pegasys.teku.ssz.backing.cache;

import java.lang.ref.SoftReference;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Keeps the delegate cache in a {@link SoftReference} to allow the cache to be GC'ed if the
 * application lacks of heap memory.
 *
 * <p>On {@link #copy()} or {@link #transfer()} also returns a {@link SoftRefIntCache} instance
 */
public class SoftRefIntCache<V> implements IntCache<V> {

  private final Supplier<IntCache<V>> cacheCtor;
  private volatile SoftReference<IntCache<V>> delegate;

  private SoftRefIntCache(IntCache<V> initialDelegate, Supplier<IntCache<V>> cacheCtor) {
    this.cacheCtor = cacheCtor;
    delegate = new SoftReference<>(initialDelegate);
  }

  public SoftRefIntCache(Supplier<IntCache<V>> cacheCtor) {
    this(cacheCtor.get(), cacheCtor);
  }

  public IntCache<V> getDelegate() {
    IntCache<V> cache = delegate.get();
    if (cache == null) {
      cache = cacheCtor.get();
      delegate = new SoftReference<>(cache);
    }
    return cache;
  }

  @Override
  public V getInt(int key, IntFunction<V> fallback) {
    return getDelegate().getInt(key, fallback);
  }

  @Override
  public V get(Integer key, Function<Integer, V> fallback) {
    return getDelegate().get(key, fallback);
  }

  @Override
  public IntCache<V> copy() {
    return new SoftRefIntCache<>(getDelegate().copy(), cacheCtor);
  }

  @Override
  public IntCache<V> transfer() {
    return new SoftRefIntCache<>(getDelegate().transfer(), cacheCtor);
  }

  @Override
  public void invalidateInt(int key) {
    getDelegate().invalidateInt(key);
  }

  @Override
  public void invalidate(Integer key) {
    getDelegate().invalidate(key);
  }

  @Override
  public void invalidateWithNewValueInt(int key, V newValue) {
    getDelegate().invalidateWithNewValueInt(key, newValue);
  }

  @Override
  public void invalidateWithNewValue(Integer key, V newValue) {
    getDelegate().invalidateWithNewValue(key, newValue);
  }

  @Override
  public void clear() {
    getDelegate().clear();
  }

  @Override
  public Optional<V> getCached(Integer key) {
    return getDelegate().getCached(key);
  }
}
