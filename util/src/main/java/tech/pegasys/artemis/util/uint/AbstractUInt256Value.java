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

package tech.pegasys.artemis.util.uint;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.artemis.util.bytes.AbstractBytes32Backed;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes32s;

import java.util.function.Supplier;

/**
 * Base implementation of all {@link UInt256Value}.
 *
 * <p>
 * Note that this is package-private: "external" {@link UInt256Value} should extend instead
 * {@link BaseUInt256Value} which add a few operations working on {@link UInt256}, but this exists
 * because {@link UInt256} itself couldn't extend {@link BaseUInt256Value} or the additional methods
 * would conflict with the ones inherited from {@link UInt256Value} (for instance, it would inherit
 * a {@code times(UInt256)} method both from {@link BaseUInt256Value#times(UInt256)} and through
 * {@code UInt256Value#times(T)} because {@code T == UInt256} in that case). In other word, this
 * class is a minor technicality that can ignore outside of this package.
 */
abstract class AbstractUInt256Value<T extends UInt256Value<T>> extends AbstractBytes32Backed
    implements UInt256Value<T> {

  private final Supplier<Counter<T>> mutableCtor;

  protected AbstractUInt256Value(Bytes32 bytes, Supplier<Counter<T>> mutableCtor) {
    super(bytes);
    this.mutableCtor = mutableCtor;
  }

  private T unaryOp(UInt256Bytes.UnaryOp op) {
    Counter<T> result = mutableCtor.get();
    op.applyOp(bytes(), result.bytes());
    return result.get();
  }

  protected T binaryOp(UInt256Value<?> value, UInt256Bytes.BinaryOp op) {
    Counter<T> result = mutableCtor.get();
    op.applyOp(bytes(), value.bytes(), result.bytes());
    return result.get();
  }

  private T binaryLongOp(long value, UInt256Bytes.BinaryLongOp op) {
    Counter<T> result = mutableCtor.get();
    op.applyOp(bytes(), value, result.bytes());
    return result.get();
  }

  private T ternaryOp(UInt256Value<?> v1, UInt256Value<?> v2, UInt256Bytes.TernaryOp op) {
    Counter<T> result = mutableCtor.get();
    op.applyOp(bytes(), v1.bytes(), v2.bytes(), result.bytes());
    return result.get();
  }

  @Override
  public T copy() {
    Counter<T> result = mutableCtor.get();
    bytes().copyTo(result.bytes());
    return result.get();
  }

  @Override
  public T plus(T value) {
    return binaryOp(value, UInt256Bytes::add);
  }

  @Override
  public T plus(long value) {
    checkArgument(value >= 0, "Invalid negative value %s", value);
    return binaryLongOp(value, UInt256Bytes::add);
  }

  @Override
  public T plusModulo(T value, UInt256 modulo) {
    return ternaryOp(value, modulo, UInt256Bytes::addModulo);
  }

  @Override
  public T minus(T value) {
    return binaryOp(value, UInt256Bytes::subtract);
  }

  @Override
  public T minus(long value) {
    checkArgument(value >= 0, "Invalid negative value %s", value);
    return binaryLongOp(value, UInt256Bytes::subtract);
  }

  @Override
  public T times(T value) {
    return binaryOp(value, UInt256Bytes::multiply);
  }

  @Override
  public T times(long value) {
    checkArgument(value >= 0, "Invalid negative value %s", value);
    return binaryLongOp(value, UInt256Bytes::multiply);
  }

  @Override
  public T timesModulo(T value, UInt256 modulo) {
    return ternaryOp(value, modulo, UInt256Bytes::multiplyModulo);
  }

  @Override
  public T dividedBy(T value) {
    return binaryOp(value, UInt256Bytes::divide);
  }

  @Override
  public T dividedBy(long value) {
    checkArgument(value >= 0, "Invalid negative value %s", value);
    return binaryLongOp(value, UInt256Bytes::divide);
  }

  @Override
  public T pow(T value) {
    return binaryOp(value, UInt256Bytes::exponent);
  }

  @Override
  public T mod(T value) {
    return binaryOp(value, UInt256Bytes::modulo);
  }

  @Override
  public T mod(long value) {
    checkArgument(value >= 0, "Invalid negative value %s", value);
    return binaryLongOp(value, UInt256Bytes::modulo);
  }

  @Override
  public Int256 signExtent(UInt256 value) {
    return new DefaultInt256(binaryOp(value, UInt256Bytes::signExtend).bytes());
  }

  @Override
  public T and(T value) {
    return binaryOp(value, Bytes32s::and);
  }

  @Override
  public T or(T value) {
    return binaryOp(value, Bytes32s::or);
  }

  @Override
  public T xor(T value) {
    return binaryOp(value, Bytes32s::xor);
  }

  @Override
  public T not() {
    return unaryOp(Bytes32s::not);
  }

  @Override
  public int compareTo(T other) {
    return UInt256Bytes.compareUnsigned(bytes(), other.bytes());
  }

  @SuppressWarnings("EqualsGetClass")
  @Override
  public boolean equals(Object other) {
    if (other == null)
      return false;
    // Note that we do want strictly class equality in this case: we don't want 2 quantity of
    // mismatching unit to be considered equal, even if they do represent the same number.
    if (this.getClass() != other.getClass())
      return false;

    UInt256Value<?> that = (UInt256Value<?>) other;
    return this.bytes().equals(that.bytes());
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  @Override
  public String toString() {
    return UInt256Bytes.toString(bytes());
  }
}
