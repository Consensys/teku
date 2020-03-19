package tech.pegasys.artemis.util.cache;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public class ArrayCache<V> implements Cache<Integer, V> {
  private V[] values;

  public ArrayCache() {
    this(16);
  }

  public ArrayCache(int initialSize) {
    this.values = createArray(initialSize);
  }

  private ArrayCache(V[] values) {
    this.values = values;
  }

  @SuppressWarnings("unchecked")
  private V[] createArray(int size) {
    return (V[]) new Object[size];
  }

  private void extend(int index) {
    int newSize = values.length;
    if (index >= newSize) {
      while (index >= newSize) {
        newSize <<= 1;
      }
      values = Arrays.copyOf(values, newSize);
    }
  }

  @Override
  public synchronized V get(Integer key, Function<Integer, V> fallback) {
    int idx = key;
    extend(idx);
    V val = values[idx];
    if (val == null) {
      val = fallback.apply(key);
      values[idx] = val;
    }
    return val;
  }

  @Override
  public synchronized Optional<V> getCached(Integer key) {
    int idx = key;
    return idx >= values.length ? Optional.empty() : Optional.ofNullable(values[idx]);
  }

  @Override
  public synchronized Cache<Integer, V> copy() {
    return new ArrayCache<>(Arrays.copyOf(values, values.length));
  }

  @Override
  public synchronized Cache<Integer, V> transfer() {
    ArrayCache<V> ret = new ArrayCache<>(values);
    values = createArray(16);
    return ret;
  }

  @Override
  public synchronized void invalidateWithNewValue(Integer key, V newValue) {
    int idx = key;
    extend(idx);
    values[idx] = newValue;
  }

  @Override
  public synchronized void invalidate(Integer key) {
    int idx = key;
    extend(idx);
    values[idx] = null;
  }

  @Override
  public synchronized void clear() {
    values = createArray(16);
  }
}
