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

package org.ethereum.beacon.ssz.fixtures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.list.AbstractListAccessor;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.type.SSZType;

/**
 * Bitfield is bit array where every bit represents status of attester with corresponding index
 *
 * <p>All methods that could change payload are cloning source, keeping instances of Bitfield
 * immutable.
 */
@SSZSerializable(listAccessor = Bitfield.BitfieldAccessor.class)
public class Bitfield {

  public static class BitfieldAccessor extends AbstractListAccessor {

    @Override
    public int getChildrenCount(Object value) {
      return ((Bitfield) value).size() / 8;
    }

    @Override
    public Object getChildValue(Object value, int idx) {
      return ((Bitfield) value).getData()[idx];
    }

    @Override
    public SSZField getListElementType(SSZField listTypeDescriptor) {
      return new SSZField(byte.class);
    }

    @Override
    public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
      return new SimpleInstanceBuilder() {
        @Override
        protected Object buildImpl(List<Object> children) {
          byte[] vals = new byte[children.size()];
          for (int i = 0; i < children.size(); i++) {
            vals[i] = ((Number) children.get(i)).byteValue();
          }
          return new Bitfield(vals);
        }
      };
    }

    @Override
    public boolean isSupported(SSZField field) {
      return Bitfield.class.isAssignableFrom(field.getRawClass());
    }
  }

  private final BitSet payload;
  private final int size; // in Bits

  private Bitfield(int size) {
    this.size = calcLength(size) * Byte.SIZE;
    this.payload = new BitSet(size);
  }

  public Bitfield(byte[] data) {
    this.size = data.length * Byte.SIZE;
    this.payload = BitSet.valueOf(data);
  }

  /**
   * Calculates attesters bitfield length
   *
   * @param num Number of attesters
   * @return Bitfield length in bytes
   */
  public static int calcLength(int num) {
    return num == 0 ? 0 : (num - 1) / Byte.SIZE + 1;
  }

  /**
   * Creates empty bitfield for estimated number of attesters
   *
   * @param validatorsCount Number of attesters
   * @return empty bitfield with correct length
   */
  public static Bitfield createEmpty(int validatorsCount) {
    return new Bitfield(validatorsCount);
  }

  public static Bitfield orBitfield(Bitfield... bitfields) {
    return orBitfield(new ArrayList<>(Arrays.asList(bitfields)));
  }

  /**
   * OR aggregation function OR aggregation of input bitfields
   *
   * @param bitfields Bitfields
   * @return All bitfields aggregated using OR
   */
  public static Bitfield orBitfield(List<Bitfield> bitfields) {
    if (bitfields.isEmpty()) return null;

    int bitfieldLen = bitfields.get(0).size();
    Bitfield aggBitfield = new Bitfield(bitfieldLen);
    for (int i = 0; i < bitfieldLen; ++i) {
      for (Bitfield bitfield : bitfields) {
        if (aggBitfield.payload.get(i) | bitfield.payload.get(i)) {
          aggBitfield.payload.set(i);
        }
      }
    }

    return aggBitfield;
  }

  /**
   * Modifies bitfield to represent attester's vote Should place its bit on the right place Doesn't
   * modify original bitfield
   *
   * @param index Index number of attester
   * @return bitfield with vote in place
   */
  public Bitfield markVote(int index) {
    Bitfield newBitfield = this.clone();
    newBitfield.payload.set(index);
    return newBitfield;
  }

  /**
   * Checks whether validator with provided index did his vote
   *
   * @param index Index number of attester
   */
  public boolean hasVoted(int index) {
    return payload.get(index);
  }

  /**
   * Calculate number of votes in provided bitfield
   *
   * @return number of votes
   */
  public int calcVotes() {
    int votes = 0;
    for (int i = 0; i < size(); ++i) {
      if (hasVoted(i)) ++votes;
    }

    return votes;
  }

  public int size() {
    return size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bitfield bitfield = (Bitfield) o;
    return size == bitfield.size && Objects.equals(payload, bitfield.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payload, size);
  }

  public byte[] getData() {
    return Arrays.copyOf(payload.toByteArray(), (size + 7) / Byte.SIZE);
  }

  public Bitfield clone() {
    return new Bitfield(getData());
  }
}
