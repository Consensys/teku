/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.util.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is the interim placeholder for structures dependent on specification The future intended
 * usage of SpecDependent would be like <code>SpecDependent.get(Spec spec)</code>
 */
@Deprecated
public class SpecDependent<V> {

  private static List<SpecDependent<?>> allDependents = new CopyOnWriteArrayList<>();

  public static <V> SpecDependent<V> of(Supplier<V> supplier) {
    SpecDependent<V> ret = new SpecDependent<>(supplier);
    allDependents.add(ret);
    return ret;
  }

  public static void resetAll() {
    allDependents.forEach(SpecDependent::reset);
  }

  private final Supplier<V> supplier;
  private volatile V cached = null;

  private SpecDependent(Supplier<V> supplier) {
    this.supplier = supplier;
  }

  public V get() {
    V cachedLoc = this.cached;
    if (cachedLoc != null) {
      return cachedLoc;
    } else {
      V newValue = supplier.get();
      this.cached = newValue;
      return newValue;
    }
  }

  public void reset() {
    cached = null;
  }

  public <R> SpecDependent<R> map(Function<V, R> mapper) {
    return of(() -> mapper.apply(get()));
  }
}
