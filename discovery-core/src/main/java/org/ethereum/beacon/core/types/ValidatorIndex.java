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
public class ValidatorIndex extends UInt64
    implements SafeComparable<ValidatorIndex>, TypeIterable<ValidatorIndex> {

  public static final ValidatorIndex MAX = new ValidatorIndex(UInt64.MAX_VALUE);
  public static final ValidatorIndex ZERO = new ValidatorIndex(UInt64.ZERO);

  public static ValidatorIndex of(int index) {
    return new ValidatorIndex(UInt64.valueOf(index));
  }

  public static ValidatorIndex of(long index) {
    return new ValidatorIndex(UInt64.valueOf(index));
  }

  public ValidatorIndex(UInt64 uint) {
    super(uint);
  }

  public ValidatorIndex(int i) {
    super(i);
  }

  @Override
  public ValidatorIndex increment() {
    return new ValidatorIndex(super.increment());
  }

  @Override
  public ValidatorIndex decrement() {
    return new ValidatorIndex(super.decrement());
  }

  @Override
  public ValidatorIndex plus(long unsignedAddend) {
    return new ValidatorIndex(super.plus(unsignedAddend));
  }

  @Override
  public ValidatorIndex plus(UInt64 addend) {
    return new ValidatorIndex(super.plus(addend));
  }

  @Override
  public ValidatorIndex minus(long unsignedSubtrahend) {
    return new ValidatorIndex(super.minus(unsignedSubtrahend));
  }

  @Override
  public ValidatorIndex minus(UInt64 subtrahend) {
    return new ValidatorIndex(super.minus(subtrahend));
  }

  @Override
  public ValidatorIndex zeroElement() {
    return ZERO;
  }
}
