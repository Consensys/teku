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
import java.lang.Long;

/**
 * An unsigned 64-bits precision number.
 */
public class UInt64 implements Comparable<UInt64> {
  private long value;
  private long remainder;

  public UInt64(long value) {
    this.value = value;
    this.remainder = 0;
  }

  public UInt64(long value, long remainder) {
    this.value = value;
    this.remainder = remainder;
  }

  public long getValue() {
    return value;
  }

  public long getRemainder() {
    return remainder;
  }

  //For familiarity for users of java.lang.Long
  public static UInt64 valueOf(long value) {
    return new UInt64(value);
  }

  //For familiarity for users of java.lang.Long
  public static UInt64 valueOf(long value, long remainder) {
    return new UInt64(value, remainder);
  }

  public UInt64 plus(long unsignedAddend) {
    this.value += unsignedAddend;
    return new UInt64(this.value);
  }

  public UInt64 plus(UInt64 addend) {
    this.value += addend.getValue();
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
    this.remainder = Long.remainderUnsigned(this.value, unsignedDivisor);
    this.value = Long.divideUnsigned(this.value, unsignedDivisor);
    return new UInt64(this.value, this.remainder);
  }

  public UInt64 dividedBy(UInt64 divisor) {
    if(divisor.value == 0) {
      throw new IllegalArgumentException("Argument 'divisor' is 0.");
    }
    this.remainder = Long.remainderUnsigned(this.value, divisor.getValue());
    this.value = Long.divideUnsigned(this.value, divisor.getValue());
    return new UInt64(this.value, this.remainder);
  }

  @Override
  public int compareTo(UInt64 uint) {
    if(this.value != uint.getValue()) {
      return Long.compareUnsigned(this.value, uint.getValue());
    } else {
      return Long.compareUnsigned(this.remainder, uint.getRemainder());
    }
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

    return this.value == uint.getValue() && this.remainder == uint.getRemainder();
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, remainder);
  }
}
