package tech.pegasys.teku.spec.util;

import java.util.function.BiFunction;
import java.util.function.Function;
import tech.pegasys.teku.spec.Spec;

public class SpecDependent<V> {

  public static <V> SpecDependent<V> of(Function<Spec, V> supplier) {
    return new SpecDependent<>(supplier);
  }

  public static <V1, V2, R> SpecDependent<R> combineAndMap(SpecDependent<V1> dep1,
      SpecDependent<V2> dep2, BiFunction<V1, V2, R> mapper) {
    return of(s -> mapper.apply(dep1.get(s), dep2.get(s)));
  }

  private class Cache {
    private final V value;
    private final Spec valueSpec;

    public Cache(V value, Spec valueSpec) {
      this.value = value;
      this.valueSpec = valueSpec;
    }
  }

  private final Function<Spec, V> supplier;
  private volatile Cache cached = new Cache(null, null);

  private SpecDependent(Function<Spec, V> supplier) {
    this.supplier = supplier;
  }

  public V get(Spec spec) {
    Cache cachedLoc = this.cached;
    if (areSpecsEqual(spec, cachedLoc.valueSpec)) {
      return cachedLoc.value;
    } else {
      V newValue = supplier.apply(spec);
      cached = new Cache(newValue, spec);
      return newValue;
    }
  }

  @Deprecated
  public V get() {
    throw new UnsupportedOperationException("TODO");
  }

  public <R> SpecDependent<R> map(Function<V, R> mapper) {
    return of(s -> mapper.apply(get(s)));
  }

  public <V1, R> SpecDependent<R> combineAndMap(SpecDependent<V1> other1, BiFunction<V, V1, R> mapper) {
    return of(s -> mapper.apply(this.get(s), other1.get(s)));
  }

  private boolean areSpecsEqual(Spec spec1, Spec spec2) {
    return spec1 == spec2;
  }
}
