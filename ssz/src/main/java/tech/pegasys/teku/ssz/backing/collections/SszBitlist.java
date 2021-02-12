/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.collections;

import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public interface SszBitlist extends SszList<SszBit> {

  static SszBitlist nullableOr(
      @Nullable SszBitlist bitlist1OrNull, @Nullable SszBitlist bitlist2OrNull) {
    return SszBitlistImpl.nullableOr(bitlist1OrNull, bitlist2OrNull);
  }

  @Override
  default SszMutableList<SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  SszBitlistSchema<SszBitlist> getSchema();

  // Bitlist methods

  SszBitlist or(SszBitlist other);

  boolean getBit(int i);

  int getBitCount();

  boolean intersects(SszBitlist other);

  boolean isSuperSetOf(final SszBitlist other);

  List<Integer> getAllSetBits();

  default IntStream streamAllSetBits() {
    return getAllSetBits().stream().mapToInt(i -> i);
  }

  int getSize();
}
