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

/** An immutable unsigned 16-bit precision integer. */
public class UInt16 extends Number implements Comparable<UInt16> {
  private static final int MODULO = (1 << 16);

  public static final UInt16 MAX_VALUE = valueOf(MODULO - 1);
  public static final UInt16 MIN_VALUE = valueOf(0);

  public static final UInt16 ZERO = MIN_VALUE;

  private final int value;

  private UInt16(int value) {
    // handle overflows and underflows
    if (value < 0) {
      int remainder = Math.abs(value) % MODULO;
      this.value = MODULO - remainder;
    } else if (value >= MODULO) {
      this.value = value % MODULO;
    } else {
      this.value = value;
    }
  }

  public UInt16(UInt16 uint) {
    this.value = uint.getValue();
  }

  public int getValue() {
    return value;
  }

  /**
   * Creates and returns a new instance of UInt24 representing the argument.
   *
   * @param unsignedValue An unsigned long.
   * @return A new UInt16 instance representing the given unsigned input.
   */
  public static UInt16 valueOf(int unsignedValue) {
    return new UInt16(unsignedValue);
  }

  /**
   * Creates and returns a new instance of UInt24 representing the argument. Parsing is done using
   * the {@link Long#parseUnsignedLong(String) Long.parseUnsignedLong} method.
   *
   * @param unsignedStringValue A string representing an unsigned long (between 0 and 2^16-1).
   * @return A new UInt16 instance representing the given unsigned input.
   * @throws NumberFormatException If the argument cannot be parsed as an unsigned integer. (i.e. <0
   *     OR >2^24-1)
   */
  public static UInt16 valueOf(String unsignedStringValue) throws NumberFormatException {
    return new UInt16(Integer.parseUnsignedInt(unsignedStringValue));
  }

  /**
   * Increments the value by 1 and returns the result. Replicates the ++ operator.
   *
   * @return A new, incremented, UInt16.
   */
  public UInt16 increment() {
    return new UInt16(this.value + 1);
  }

  /**
   * Decrements the value by 1 and return the result. Replicates the -- operator.
   *
   * @return A new, decremented, UInt16.
   */
  public UInt16 decrement() {
    return new UInt16(this.value - 1);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt24.
   *
   * @param unsignedAddend An unsigned long to add.
   * @return A new UInt16 containing the result of the addition operation.
   */
  public UInt16 plus(int unsignedAddend) {
    return new UInt16(this.value + unsignedAddend);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt24.
   *
   * @param addend A UInt16 representing an unsigned long to add.
   * @return A new UInt16 containing the result of the addition operation.
   */
  public UInt16 plus(UInt16 addend) {
    return new UInt16(this.value + addend.getValue());
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt16.
   *
   * @param unsignedSubtrahend An unsigned long to subtract.
   * @return A new UInt16 containing the result of the subtraction operation.
   */
  public UInt16 minus(int unsignedSubtrahend) {
    return new UInt16(this.value - unsignedSubtrahend);
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt16.
   *
   * @param subtrahend A UInt16 representing an unsigned long to subtract.
   * @return A new UInt16 containing the result of the subtraction operation.
   */
  public UInt16 minus(UInt16 subtrahend) {
    return new UInt16(this.value - subtrahend.getValue());
  }

  @Override
  public int compareTo(UInt16 uint) {
    return Integer.compareUnsigned(this.value, uint.getValue());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof UInt16)) {
      return false;
    }

    UInt16 uint = (UInt16) o;

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
