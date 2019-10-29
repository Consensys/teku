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

package org.ethereum.beacon.core.types;

import java.util.function.Function;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable(serializeAs = UInt64.class)
public class ShardNumber extends UInt64
    implements SafeComparable<ShardNumber>, TypeIterable<ShardNumber> {

  public static final ShardNumber ZERO = of(0);

  public static ShardNumber of(int i) {
    return new ShardNumber(UInt64.valueOf(i));
  }

  public static ShardNumber of(long i) {
    return new ShardNumber(UInt64.valueOf(i));
  }

  public static ShardNumber of(UInt64 i) {
    return new ShardNumber(i);
  }

  public ShardNumber(UInt64 uint) {
    super(uint);
  }

  public ShardNumber(int i) {
    super(i);
  }

  public ShardNumber plusModulo(long addend, ShardNumber divisor) {
    return plusModulo(UInt64.valueOf(addend), divisor);
  }

  public ShardNumber plusModulo(UInt64 addend, ShardNumber divisor) {
    return new ShardNumber(this.plus(addend).modulo(divisor));
  }

  public ShardNumber minusModulo(long subtrahend, ShardNumber divisor) {
    return minusModulo(UInt64.valueOf(subtrahend), divisor);
  }

  public ShardNumber minusModulo(UInt64 subtrahend, ShardNumber divisor) {
    return new ShardNumber(this.minus(subtrahend).modulo(divisor));
  }

  public ShardNumber safeModulo(Function<UInt64, UInt64> safeCalc, ShardNumber divisor) {
    return new ShardNumber(safeCalc.apply(this).modulo(divisor));
  }

  @Override
  public ShardNumber increment() {
    return new ShardNumber(super.increment());
  }

  @Override
  public ShardNumber decrement() {
    return new ShardNumber(super.decrement());
  }

  @Override
  public ShardNumber zeroElement() {
    return ZERO;
  }
}
