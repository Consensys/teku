package net.consensys.beaconchain.util.uint;

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.beaconchain.util.bytes.Bytes32;
import net.consensys.beaconchain.util.bytes.MutableBytes32;

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
