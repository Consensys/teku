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
  public static final UInt64 MAX_VALUE = valueOfUnsafe(-1);
  public static final UInt64 MIN_VALUE = valueOf(0);


  private long value;

  private UInt64(long value) {
    this.value = value;
  }

  public long getValue() {
    return value;
  }

  //For familiarity for users of java.lang.Long
  public static UInt64 valueOf(long unsignedValue) {
    return new UInt64(unsignedValue);
  }

  private static UInt64 valueOfUnsafe(long unsafeValue) {
    return new UInt64(unsafeValue);
  }

  public UInt64 increment() {
    ++this.value;
    return new UInt64(this.value);
  }

  public UInt64 plus(long unsignedAddend) {
    this.value += unsignedAddend;
    return new UInt64(this.value);
  }

  public UInt64 plus(UInt64 addend) {
    this.value += addend.getValue();
    return new UInt64(this.value);
  }

  public UInt64 decrement() {
    --this.value;
    return new UInt64(this.value);
  }

  public UInt64 minus(long unsignedSubtrahend) {
    this.value -= unsignedSubtrahend;
    return new UInt64(this.value);
  }

  public UInt64 minus(UInt64 subtrahend) {
    this.value -= subtrahend.getValue();
    return new UInt64(this.value);
  }

  public UInt64 times(long unsignedMultiplier) {
    this.value *= unsignedMultiplier;
    return new UInt64(this.value);
  }

  public UInt64 times(UInt64 multiplier) {
    this.value *= multiplier.getValue();
    return new UInt64(this.value);
  }

  public UInt64 dividedBy(long unsignedDivisor) {
    if(unsignedDivisor == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    this.value = Long.divideUnsigned(this.value, unsignedDivisor);
    return new UInt64(this.value);
  }

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

    return this.value == uint.getValue();
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
