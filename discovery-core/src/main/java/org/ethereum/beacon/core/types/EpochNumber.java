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

import javax.annotation.Nullable;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.SlotNumber.EpochLength;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable(serializeAs = UInt64.class)
public class EpochNumber extends UInt64
    implements SafeComparable<EpochNumber>, TypeIterable<EpochNumber> {

  public static final EpochNumber ZERO = of(0);

  public static EpochNumber of(int epochNum) {
    return new EpochNumber(UInt64.valueOf(epochNum));
  }

  public static EpochNumber castFrom(UInt64 epochNum) {
    return new EpochNumber(epochNum);
  }

  public EpochNumber(UInt64 uint) {
    super(uint);
  }

  public EpochNumber(int i) {
    super(i);
  }

  @Override
  public EpochNumber plus(long unsignedAddend) {
    return new EpochNumber(super.plus(unsignedAddend));
  }

  public EpochNumber plusModulo(long addend, EpochNumber divisor) {
    return plusModulo(UInt64.valueOf(addend), divisor);
  }

  public EpochNumber plusModulo(UInt64 addend, EpochNumber divisor) {
    return new EpochNumber(this.plus(addend).modulo(divisor));
  }

  public EpochNumber minus(EpochNumber subtract) {
    return new EpochNumber(super.minus(subtract));
  }

  public EpochNumber plus(EpochNumber addend) {
    return new EpochNumber(super.plus(addend));
  }

  public SlotNumber mul(EpochLength epochLength) {
    return new SlotNumber(times(epochLength));
  }

  public EpochNumber modulo(EpochNumber divisor) {
    return new EpochNumber(super.modulo(divisor));
  }

  @Override
  public EpochNumber increment() {
    return new EpochNumber(super.increment());
  }

  @Override
  public EpochNumber decrement() {
    return new EpochNumber(super.decrement());
  }

  public EpochNumber half() {
    return new EpochNumber(dividedBy(2));
  }

  @Override
  public EpochNumber zeroElement() {
    return ZERO;
  }

  public String toString(@Nullable SpecConstants spec) {
    return spec == null ? super.toString() : this.minus(spec.getGenesisEpoch()).toString();
  }
}
