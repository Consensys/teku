/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.view;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.MutableBitlist;
import tech.pegasys.teku.ssz.backing.ListViewRead;

public class ViewBackedBitlist implements Bitlist {
  private final ListViewRead<BasicViews.BitView> bitlistView;
  private final int size;
  private final long maxSize;

  public ViewBackedBitlist(final ListViewRead<BasicViews.BitView> bitlistView) {
    this.bitlistView = bitlistView;
    this.size = bitlistView.size();
    this.maxSize = bitlistView.getType().getMaxLength();
  }

  @Override
  public boolean getBit(final int i) {
    return bitlistView.get(i).get();
  }

  @Override
  public int getBitCount() {
    return Math.toIntExact(streamAllSetBits().count());
  }

  @Override
  public boolean intersects(final Bitlist other) {
    return other.streamAllSetBits().anyMatch(this::getBit);
  }

  @Override
  public boolean isSuperSetOf(final Bitlist other) {
    return other.streamAllSetBits().allMatch(this::getBit);
  }

  @Override
  public List<Integer> getAllSetBits() {
    return streamAllSetBits().boxed().collect(Collectors.toList());
  }

  @Override
  public IntStream streamAllSetBits() {
    return IntStream.range(0, size).filter(this::getBit);
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public int getCurrentSize() {
    return size;
  }

  @Override
  public MutableBitlist copy() {
    MutableBitlist ret = MutableBitlist.create(size, maxSize);
    for (int i = 0; i < size; i++) {
      if (getBit(i)) {
        ret.setBit(i);
      }
    }
    return ret;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }

    return Bitlist.equals(this, (Bitlist) o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Bitlist.hashBits(this), size, maxSize);
  }
}
