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

package tech.pegasys.artemis.util.collections;

import com.google.common.base.Objects;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

public class Bitvector extends Bitlist {
  public static Bitvector EMPTY = of(0, BytesValue.EMPTY);

  private Bitvector(int size, BytesValue bytes) {
    super(size, bytes, -1);
  }

  public static Bitvector of(int size, BytesValue bytes) {
    return new Bitvector(size, bytes);
  }

  public static Bitvector of(int size, long bytes) {
    UInt64 blank = UInt64.valueOf(bytes);
    if (blank.getUsedBitCount() > size) {
      throw new IndexOutOfBoundsException(
          String.format("Input data %s exceeds Bitvector size of %s", bytes, size));
    }

    int neededBytes = (size + 7) / 8;
    return new Bitvector(size, blank.toBytesValue().slice(0, neededBytes));
  }

  @Override
  void verifyBitModification(int bitIndex) {
    if (bitIndex >= size()) {
      throw new IndexOutOfBoundsException(
          String.format("An attempt to set bit #%s for Bitvector with size %s", bitIndex, size()));
    }
  }

  @Override
  public Bitvector setBit(int bitIndex, int bit) {
    Bitlist bitlist = super.setBit(bitIndex, bit);
    return new Bitvector(size(), bitlist.getWrapped());
  }

  @Override
  public Bitvector setBit(int bitIndex, boolean bit) {
    Bitlist bitlist = super.setBit(bitIndex, bit);
    return new Bitvector(size(), bitlist.getWrapped());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bitvector bitVector = (Bitvector) o;
    return size() == bitVector.size() && wrapped.equals(((Bitvector) o).wrapped);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), size());
  }

  @Override
  public int byteSize() {
    return (size() + 7) / 8;
  }

  @Override
  public Bitvector shl(int i) {
    Bitlist bitlist = super.shl(i);
    return new Bitvector(size(), bitlist.getWrapped());
  }

  @Override
  public Bitvector and(Bitlist other) {
    Bitlist bitlist = super.and(other);
    return new Bitvector(bitlist.size(), bitlist.getWrapped());
  }

  @Override
  public Bitvector or(Bitlist other) {
    Bitlist bitlist = super.or(other);
    return new Bitvector(size(), bitlist.getWrapped());
  }
}
