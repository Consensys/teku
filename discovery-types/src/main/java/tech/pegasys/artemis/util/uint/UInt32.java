/*
 * Copyright 2019 ConsenSys AG.
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

import java.util.Objects;

/** An immutable unsigned 32-bit precision integer. */
public class UInt32 extends Number implements Comparable<UInt32> {
  private static final long MODULO = 1L << 32;

  public static final UInt32 MAX_VALUE = valueOf(-1);
  public static final UInt32 MIN_VALUE = valueOf(0);

  public static final UInt32 ZERO = MIN_VALUE;

  private final int value;

  private UInt32(long value) {
    // handle overflows and underflows
    if (value < 0) {
      long remainder = Math.abs(value) % MODULO;
      this.value = (int) (MODULO - remainder);
    } else if (value >= MODULO) {
      this.value = (int) (value % MODULO);
    } else {
      this.value = (int) value;
    }
  }

  public UInt32(UInt32 uint) {
    this.value = uint.getValue();
  }

  public int getValue() {
    return value;
  }

  /**
   * Creates and returns a new instance of UInt32 representing the argument.
   *
   * @param unsignedValue An unsigned long.
   * @return A new UInt32 instance representing the given unsigned input.
   */
  public static UInt32 valueOf(long unsignedValue) {
    return new UInt32(unsignedValue);
  }

  /**
   * Creates and returns a new instance of UInt32 representing the argument. Parsing is done using
   * the {@link Long#parseUnsignedLong(String) Long.parseUnsignedLong} method.
   *
   * @param unsignedStringValue A string representing an unsigned long (between 0 and 2^32-1).
   * @return A new UInt32 instance representing the given unsigned input.
   * @throws NumberFormatException If the argument cannot be parsed as an unsigned integer. (i.e. <0
   *     OR >2^32-1)
   */
  public static UInt32 valueOf(String unsignedStringValue) throws NumberFormatException {
    return new UInt32(Integer.parseUnsignedInt(unsignedStringValue));
  }

  /**
   * Increments the value by 1 and returns the result. Replicates the ++ operator.
   *
   * @return A new, incremented, UInt32.
   */
  public UInt32 increment() {
    return new UInt32(this.value + 1);
  }

  /**
   * Decrements the value by 1 and return the result. Replicates the -- operator.
   *
   * @return A new, decremented, UInt32.
   */
  public UInt32 decrement() {
    return new UInt32(this.value - 1);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt32.
   *
   * @param unsignedAddend An unsigned long to add.
   * @return A new UInt32 containing the result of the addition operation.
   */
  public UInt32 plus(int unsignedAddend) {
    return new UInt32(this.value + unsignedAddend);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt32.
   *
   * @param addend A UInt32 representing an unsigned long to add.
   * @return A new UInt32 containing the result of the addition operation.
   */
  public UInt32 plus(UInt32 addend) {
    return new UInt32(this.value + addend.getValue());
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt32.
   *
   * @param unsignedSubtrahend An unsigned long to subtract.
   * @return A new UInt32 containing the result of the subtraction operation.
   */
  public UInt32 minus(int unsignedSubtrahend) {
    return new UInt32(this.value - unsignedSubtrahend);
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt32.
   *
   * @param subtrahend A UInt32 representing an unsigned long to subtract.
   * @return A new UInt32 containing the result of the subtraction operation.
   */
  public UInt32 minus(UInt32 subtrahend) {
    return new UInt32(this.value - subtrahend.getValue());
  }

  @Override
  public int compareTo(UInt32 uint) {
    return Integer.compareUnsigned(this.value, uint.getValue());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof UInt32)) {
      return false;
    }

    UInt32 uint = (UInt32) o;

    return Integer.compareUnsigned(this.value, uint.getValue()) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return Integer.toUnsignedString(this.value);
  }

  @Override
  public int intValue() {
    return getValue();
  }

  @Override
  public long longValue() {
    return getValue();
  }

  @Override
  public float floatValue() {
    return getValue();
  }

  @Override
  public double doubleValue() {
    return getValue();
  }
}
