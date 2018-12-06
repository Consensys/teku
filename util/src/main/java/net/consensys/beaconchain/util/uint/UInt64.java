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

package net.consensys.beaconchain.util.uint;

import java.util.Objects;

/**
 * An unsigned 64-bit precision integer.
 */
public class UInt64 implements Comparable<UInt64> {
  //Yes, this looks weird. If it helps, think of this as (Long.MAX_VALUE<<1)+1.
  public static final UInt64 MAX_VALUE = valueOf(-1);
  public static final UInt64 MIN_VALUE = valueOf(0);

  private long value;

  private UInt64(long value) {
    this.value = value;
  }

  public UInt64(UInt64 uint) {
    this.value = uint.getValue();
  }

  public long getValue() {
    return value;
  }

  /**
   * Creates and returns a new instance of UInt64 representing the argument.
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1,
   * so unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   * <p>If this is not desired, please use {@link #valueOf(String)} to parse larger unsigned numbers.
   *
   * @param unsignedValue An unsigned long. Please see note above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 instance representing the given unsigned input.
   */
  public static UInt64 valueOf(long unsignedValue) {
    return new UInt64(unsignedValue);
  }

  /**
   * Creates and returns a new instance of UInt64 representing the argument.
   * Parsing is done using the {@link java.lang.Long#parseUnsignedLong(String) Long.parseUnsignedLong} method.
   *
   * @param unsignedStringValue A string representing an unsigned long (between 0 and 2^64-1).
   * @return A new UInt64 instance representing the given unsigned input.
   * @throws NumberFormatException If the argument cannot be parsed as an unsigned integer. (i.e. <0 OR >2^64-1)
   */
  public static UInt64 valueOf(String unsignedStringValue) throws NumberFormatException {
    return new UInt64(Long.parseUnsignedLong(unsignedStringValue));
  }

  /**
   * Increments the object by 1. Replicates the ++ operator.
   *
   * @return A new, incremented, UInt64.
   */
  public UInt64 increment() {
    ++this.value;
    return new UInt64(this.value);
  }

  /**
   * Decrements the object by 1. Replicates the -- operator.
   *
   * @return A new, decremented, UInt64.
   */
  public UInt64 decrement() {
    --this.value;
    return new UInt64(this.value);
  }

  /**
   * Adds the addend passed in the argument to specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1,
   * so unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   * <p>If this is not desired, please use {@link #plus(UInt64)}, i.e. plus(UInt64.valueOf(String))
   *
   * @param unsignedAddend An unsigned long to add. Please see note above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the addition operation.
   */
  public UInt64 plus(long unsignedAddend) {
    this.value += unsignedAddend;
    return new UInt64(this.value);
  }

  /**
   * Adds the addend passed in the argument to specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   *
   * @param addend A UInt64 representing an unsigned long to add.
   * @return A new UInt64 containing the result of the addition operation.
   */
  public UInt64 plus(UInt64 addend) {
    this.value += addend.getValue();
    return new UInt64(this.value);
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1,
   * so unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   * <p>If this is not desired, please use {@link #minus(UInt64)}, i.e. minus(UInt64.valueOf(String))
   *
   * @param unsignedSubtrahend An unsigned long to subtract. Please see note above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the subtraction operation.
   */
  public UInt64 minus(long unsignedSubtrahend) {
    this.value -= unsignedSubtrahend;
    return new UInt64(this.value);
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   *
   * @param subtrahend A UInt64 representing an unsigned long to subtract.
   * @return A new UInt64 containing the result of the subtraction operation.
   */
  public UInt64 minus(UInt64 subtrahend) {
    this.value -= subtrahend.getValue();
    return new UInt64(this.value);
  }

  /**
   * Multiplies the multiplier passed in the argument by the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1,
   * so unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   * <p>If this is not desired, please use {@link #times(UInt64)}, i.e. times(UInt64.valueOf(String))
   *
   * @param unsignedMultiplier An unsigned long to multiply. Please see note above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the multiplication operation.
   */
  public UInt64 times(long unsignedMultiplier) {
    this.value *= unsignedMultiplier;
    return new UInt64(this.value);
  }

  /**
   * Multiplies the multiplier passed in the argument by the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   *
   * @param multiplier A UInt64 representing an unsigned long to multiply.
   * @return A new UInt64 containing the result of the multiplication operation.
   */
  public UInt64 times(UInt64 multiplier) {
    this.value *= multiplier.getValue();
    return new UInt64(this.value);
  }

  /**
   * Divides the divisor passed in the argument by the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1,
   * so unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   * <p>If this is not desired, please use {@link #dividedBy(UInt64)}, i.e. dividedBy(UInt64.valueOf(String))
   *
   * @param unsignedDivisor An unsigned long to divide by. Please see note above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the integer part of the result of the division operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 dividedBy(long unsignedDivisor) {
    if(unsignedDivisor == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    this.value = Long.divideUnsigned(this.value, unsignedDivisor);
    return new UInt64(this.value);
  }

  /**
   * Divides the divisor passed in the argument by the specified object. The object is updated with the result,
   * and the result is also returned as a new UInt64.
   *
   * @param divisor A UInt64 representing an unsigned long to divide by.
   * @return A new UInt64 containing the integer part of the result of the division operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 dividedBy(UInt64 divisor) {
    if(divisor.value == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    this.value = Long.divideUnsigned(this.value, divisor.getValue());
    return new UInt64(this.value);
  }

  @Override
  public int compareTo(UInt64 uint) {
    return Long.compareUnsigned(this.value, uint.getValue());
  }

  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    }

    if(!(o instanceof UInt64)) {
      return false;
    }

    UInt64 uint = (UInt64) o;

    return Long.compareUnsigned(this.value,uint.getValue()) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(this.value);
  }
}
