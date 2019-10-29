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

import com.google.common.primitives.UnsignedLongs;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Random;
import tech.pegasys.artemis.util.bytes.Bytes8;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** An immutable unsigned 64-bit precision integer. */
public class UInt64 extends Number implements Comparable<UInt64> {
  // Yes, this looks weird. If it helps, think of this as (Long.MAX_VALUE<<1)+1.
  public static final UInt64 MAX_VALUE = valueOf(-1);
  public static final UInt64 MIN_VALUE = valueOf(0);

  public static final UInt64 ZERO = MIN_VALUE;
  public static final int BIT_SIZE = 64;

  private final long value;

  protected UInt64(long value) {
    this.value = value;
  }

  public UInt64(UInt64 uint) {
    this.value = uint.getValue();
  }

  public long getValue() {
    return value;
  }

  public int getIntValue() {
    assert value >= 0 && value <= Integer.MAX_VALUE;
    return (int) value;
  }

  /**
   * Creates and returns a new instance of UInt64 representing the argument.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #valueOf(String)} to parse larger unsigned
   * numbers.
   *
   * @param unsignedValue An unsigned long. Please see note above about support for large unsigned
   *     longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 instance representing the given unsigned input.
   */
  public static UInt64 valueOf(long unsignedValue) {
    return new UInt64(unsignedValue);
  }

  /**
   * Creates and returns a new instance of UInt64 representing the argument. Parsing is done using
   * the {@link java.lang.Long#parseUnsignedLong(String) Long.parseUnsignedLong} method.
   *
   * @param unsignedStringValue A string representing an unsigned long (between 0 and 2^64-1).
   * @return A new UInt64 instance representing the given unsigned input.
   * @throws NumberFormatException If the argument cannot be parsed as an unsigned integer. (i.e. <0
   *     OR >2^64-1)
   */
  public static UInt64 valueOf(String unsignedStringValue) throws NumberFormatException {
    return new UInt64(Long.parseUnsignedLong(unsignedStringValue));
  }

  public static UInt64 fromBytesBigEndian(Bytes8 bytes) {
    return fromBytes(bytes, true);
  }

  public static UInt64 fromBytesLittleEndian(Bytes8 bytes) {
    return fromBytes(bytes, false);
  }

  private static UInt64 fromBytes(Bytes8 bytes, boolean bigEndian) {
    ByteBuffer byteBuffer =
        ByteBuffer.allocate(Long.BYTES)
            .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
            .put(bytes.getArrayUnsafe());
    byteBuffer.rewind();
    return valueOf(byteBuffer.getLong());
  }

  /**
   * Constructs a randomly generated value.
   *
   * @param rng random number generator.
   * @return random value.
   */
  public static UInt64 random(Random rng) {
    return valueOf(rng.nextLong());
  }

  /**
   * Compares two values and returns the least one.
   *
   * @param value1 first value.
   * @param value2 second value.
   * @return the one which is less than another.
   */
  public static UInt64 min(UInt64 value1, UInt64 value2) {
    if (value1.compareTo(value2) < 0) {
      return value2;
    } else {
      return value1;
    }
  }

  /**
   * Increments the value by 1 and returns the result. Replicates the ++ operator.
   *
   * @return A new, incremented, UInt64.
   */
  public UInt64 increment() {
    return new UInt64(this.value + 1);
  }

  /**
   * Decrements the value by 1 and return the result. Replicates the -- operator.
   *
   * @return A new, decremented, UInt64.
   */
  public UInt64 decrement() {
    return new UInt64(this.value - 1);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt64.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #plus(UInt64)}, i.e. plus(UInt64.valueOf(String))
   *
   * @param unsignedAddend An unsigned long to add. Please see note above about support for large
   *     unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the addition operation.
   */
  public UInt64 plus(long unsignedAddend) {
    return new UInt64(this.value + unsignedAddend);
  }

  /**
   * Adds the addend passed in the argument to specified object. The result is returned as a new
   * UInt64.
   *
   * @param addend A UInt64 representing an unsigned long to add.
   * @return A new UInt64 containing the result of the addition operation.
   */
  public UInt64 plus(UInt64 addend) {
    return new UInt64(this.value + addend.getValue());
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt64.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #minus(UInt64)}, i.e.
   * minus(UInt64.valueOf(String))
   *
   * @param unsignedSubtrahend An unsigned long to subtract. Please see note above about support for
   *     large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the subtraction operation.
   */
  public UInt64 minus(long unsignedSubtrahend) {
    return new UInt64(this.value - unsignedSubtrahend);
  }

  /**
   * Subtracts the subtrahend passed in the argument from the specified object. The result is
   * returned as a new UInt64.
   *
   * @param subtrahend A UInt64 representing an unsigned long to subtract.
   * @return A new UInt64 containing the result of the subtraction operation.
   */
  public UInt64 minus(UInt64 subtrahend) {
    return new UInt64(this.value - subtrahend.getValue());
  }

  /**
   * Saturation subtraction.
   *
   * @param subtrahend A UInt64 representing an unsigned long to subtract.
   * @return {@link #MIN_VALUE} if underflowed, otherwise, result of {@link #minus(UInt64)}.
   */
  public UInt64 minusSat(UInt64 subtrahend) {
    if (this.compareTo(subtrahend) < 0) {
      return MIN_VALUE;
    } else {
      return minus(subtrahend);
    }
  }

  public UInt64 minusSat(long subtrahend) {
    return minusSat(UInt64.valueOf(subtrahend));
  }

  /**
   * Saturation addition.
   *
   * @param addend A UInt64 representing an unsigned long to add.
   * @return {@link #MAX_VALUE} if overflowed, otherwise, result of {@link #plus(UInt64)}.
   */
  public UInt64 plusSat(UInt64 addend) {
    UInt64 res = this.plus(addend);
    if (res.compareTo(this) <= 0 && addend.compareTo(UInt64.ZERO) > 0) {
      return MAX_VALUE;
    } else {
      return res;
    }
  }

  public UInt64 plusSat(long addend) {
    return plusSat(UInt64.valueOf(addend));
  }

  /**
   * Multiplies the multiplier passed in the argument by the specified object. The result is
   * returned as a new UInt64.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #times(UInt64)}, i.e.
   * times(UInt64.valueOf(String))
   *
   * @param unsignedMultiplier An unsigned long to multiply. Please see note above about support for
   *     large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the multiplication operation.
   */
  public UInt64 times(long unsignedMultiplier) {
    return new UInt64(this.value * unsignedMultiplier);
  }

  /**
   * Multiplies the multiplier passed in the argument by the specified object. The result is
   * returned as a new UInt64.
   *
   * @param multiplier A UInt64 representing an unsigned long to multiply.
   * @return A new UInt64 containing the result of the multiplication operation.
   */
  public UInt64 times(UInt64 multiplier) {
    return new UInt64(this.value * multiplier.getValue());
  }

  /**
   * Divides the divisor passed in the argument by the specified object. The result is returned as a
   * new UInt64.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #dividedBy(UInt64)}, i.e.
   * dividedBy(UInt64.valueOf(String))
   *
   * @param unsignedDivisor An unsigned long to divide by. Please see note above about support for
   *     large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the integer part of the result of the division operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 dividedBy(long unsignedDivisor) {
    if (unsignedDivisor == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    return new UInt64(Long.divideUnsigned(this.value, unsignedDivisor));
  }

  /**
   * Divides the divisor passed in the argument by the specified object. The result is returned as a
   * new UInt64.
   *
   * @param divisor A UInt64 representing an unsigned long to divide by.
   * @return A new UInt64 containing the integer part of the result of the division operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 dividedBy(UInt64 divisor) {
    if (divisor.getValue() == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    return new UInt64(Long.divideUnsigned(this.value, divisor.getValue()));
  }

  /**
   * Computes the modulo of the divisor passed in the argument and the dividend object. The result
   * is returned as a new UInt64.
   *
   * <p><strong>NOTE: </strong> Java will not natively allow integer literals larger than 2^63-1, so
   * unsigned integers between 2^63-1 and 2^64-1 MUST be representated as a negative literal.
   *
   * <p>If this is not desired, please use {@link #modulo(UInt64)}, i.e.
   * modulo(UInt64.valueOf(String))
   *
   * @param unsignedDivisor An unsigned long to divide by when computing modulus. Please see note
   *     above about support for large unsigned longs between 2^63-1 and 2^64-1.
   * @return A new UInt64 containing the result of the modulo operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 modulo(long unsignedDivisor) {
    if (unsignedDivisor == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    return new UInt64(UnsignedLongs.remainder(this.value, unsignedDivisor));
  }

  /**
   * Computes the modulo of the divisor passed in the argument and the specified dividend object.
   * The result is returned as a new UInt64.
   *
   * @param divisor A UInt64 representing an unsigned long to divide by when computing modulus.
   * @return A new UInt64 containing the result of the modulo operation.
   * @throws IllegalArgumentException If the divisor is 0.
   */
  public UInt64 modulo(UInt64 divisor) {
    if (divisor.getValue() == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    return new UInt64(Long.remainderUnsigned(this.value, divisor.getValue()));
  }

  /**
   * Computes bitwise {@code OR} operation.
   *
   * @return A new UInt64 containing the result of the {@code OR} operation.
   */
  public UInt64 or(UInt64 uint) {
    return new UInt64(this.value | uint.value);
  }

  public UInt64 and(UInt64 uint) {
    return new UInt64(this.value & uint.value);
  }

  /**
   * Shifts a bit pattern of the value to the left.
   *
   * @param number a number of positions to shift.
   * @return shifted value.
   */
  public UInt64 shl(int number) {
    return new UInt64(value << number);
  }

  public UInt64 shr(int number) {
    return new UInt64(value >>> number);
  }

  public int getUsedBitCount() {
    return BIT_SIZE - Long.numberOfLeadingZeros(value);
  }

  /**
   * Converts value to {@link Bytes8} value. Uses {@link Bytes8#longToBytes8(long)} method.
   *
   * @return a {@link Bytes8} value.
   */
  public Bytes8 toBytes8() {
    return Bytes8.longToBytes8(value);
  }

  /**
   * Converts value to {@link Bytes8} little endian value. Uses {@link
   * Bytes8#longToBytes8LittleEndian(long)} method.
   *
   * @return a {@link Bytes8} value.
   */
  public Bytes8 toBytes8LittleEndian() {
    return Bytes8.longToBytes8LittleEndian(value);
  }

  // TODO should be type safe with the respect to custom types
  @Override
  public int compareTo(UInt64 uint) {
    return Long.compareUnsigned(this.value, uint.getValue());
  }

  public Bytes8 toBytesBigEndian() {
    byte[] array =
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
    return Bytes8.wrap(array);
  }

  public BytesValue toBytesValue() {
    byte[] array =
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    return BytesValue.wrap(array);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof UInt64)) {
      return false;
    }

    UInt64 uint = (UInt64) o;

    return Long.compareUnsigned(this.value, uint.getValue()) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(this.value);
  }

  @Override
  public int intValue() {
    return getIntValue();
  }

  @Override
  public long longValue() {
    return getValue();
  }

  public BigInteger toBI() {
    return new BigInteger(1, toBytesBigEndian().extractArray());
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
