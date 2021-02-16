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
import tech.pegasys.teku.ssz.backing.SszMutableVector;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public interface SszBitvector extends SszVector<SszBit> {

  @Override
  default SszMutableVector<SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  SszBitvectorSchema<? extends SszBitvector> getSchema();

  // Bitlist methods

  SszBitvector withBit(int i);

  /** Returns individual bit value */
  boolean getBit(int i);

  /** Returns the number of bits set to {@code true} in this {@code SszBitlist}. */
  int getBitCount();

  /** Returns indexes of all bits set in this {@link SszBitvector} */
  List<Integer> getAllSetBits();

  /** Streams indexes of all bits set in this {@link SszBitvector} */
  default IntStream streamAllSetBits() {
    return getAllSetBits().stream().mapToInt(i -> i);
  }
}
