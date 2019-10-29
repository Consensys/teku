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

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable(serializeAs = UInt64.class)
public class Gwei extends UInt64 implements SafeComparable<Gwei> {
  private static final long GWEI_PER_ETHER = 1_000_000_000L;

  public static final Gwei ZERO = of(0);

  public Gwei(UInt64 uint) {
    super(uint);
  }

  public static Gwei ofEthers(int ethers) {
    return of(GWEI_PER_ETHER * ethers);
  }

  public static Gwei of(long gweis) {
    return new Gwei(UInt64.valueOf(gweis));
  }

  public static Gwei castFrom(UInt64 gweis) {
    return new Gwei(gweis);
  }

  public Gwei plus(Gwei addend) {
    return new Gwei(super.plus(addend));
  }

  public Gwei minus(Gwei subtrahend) {
    return new Gwei(super.minus(subtrahend));
  }

  @Override
  public Gwei times(UInt64 unsignedMultiplier) {
    return new Gwei(super.times(unsignedMultiplier));
  }

  @Override
  public Gwei dividedBy(UInt64 divisor) {
    return new Gwei(super.dividedBy(divisor));
  }

  @Override
  public Gwei dividedBy(long divisor) {
    return new Gwei(super.dividedBy(divisor));
  }

  public Gwei mulDiv(Gwei multiplier, Gwei divisor) {
    return new Gwei(this.times(multiplier).dividedBy(divisor));
  }

  public Gwei times(int times) {
    return new Gwei(super.times(times));
  }

  @Override
  public String toString() {
    return getValue() % GWEI_PER_ETHER == 0
        ? (getValue() / GWEI_PER_ETHER) + " Eth"
        : (getValue() / (double) GWEI_PER_ETHER) + " Eth";
  }
}
