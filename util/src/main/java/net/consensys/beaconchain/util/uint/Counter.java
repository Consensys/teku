/*
 * Copyright 2018 ConsenSys AG.
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

package net.consensys.artemis.util.uint;

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.artemis.util.bytes.Bytes32;
import net.consensys.artemis.util.bytes.MutableBytes32;

import java.util.function.Function;

/**
 * A mutable 256-bits (32 bytes) integer value.
 *
 * <p>
 * A {@link Counter} value can be modified through the provided operations ({@link #increment}, ...)
 * of by accessing/mutating the underlying bytes directly with {@link #bytes}. The value itself must
 * be obtained through {@link #get}.
 *
 * @param <T> The concrete type of the value.
 */
public class Counter<T extends UInt256Value<T>> {

  private final MutableBytes32 bytes;
  private final T value;

  // Kept around for copy()
  private final Function<Bytes32, T> wrapFct;

  protected Counter(Function<Bytes32, T> wrapFct) {
    this(MutableBytes32.create(), wrapFct);
  }

  protected Counter(MutableBytes32 bytes, Function<Bytes32, T> wrapFct) {
    this.bytes = bytes;
    this.value = wrapFct.apply(bytes);
    this.wrapFct = wrapFct;
  }

  public T get() {
    return value;
  }

  public MutableBytes32 bytes() {
    return bytes;
  }

  public Counter<T> copy() {
    return new Counter<>(bytes.mutableCopy(), wrapFct);
  }

  public void increment() {
    increment(1);
  }

  public void increment(long increment) {
    checkArgument(increment >= 0, "Invalid negative increment %s", increment);
    UInt256Bytes.add(bytes, increment, bytes);
  }

  public void increment(T increment) {
    UInt256Bytes.add(bytes, increment.bytes(), bytes);
  }

  public void decrement() {
    decrement(1);
  }

  public void decrement(long decrement) {
    checkArgument(decrement >= 0, "Invalid negative decrement %s", decrement);
    UInt256Bytes.subtract(bytes, decrement, bytes);
  }

  public void decrement(T decrement) {
    UInt256Bytes.subtract(bytes, decrement.bytes(), bytes);
  }

  public void set(T value) {
    value.bytes().copyTo(bytes);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
