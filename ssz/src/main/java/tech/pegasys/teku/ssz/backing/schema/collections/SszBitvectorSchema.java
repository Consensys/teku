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

package tech.pegasys.teku.ssz.backing.schema.collections;

import java.util.stream.StreamSupport;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public interface SszBitvectorSchema<SszBitvectorT extends SszBitvector>
    extends SszVectorSchema<SszBit, SszBitvectorT> {

  static SszBitvectorSchema<SszBitvector> create(long length) {
    return new SszBitvectorSchemaImpl(length);
  }

  SszBitvectorT ofBits(int... setBitIndexes);

  default SszBitvectorT ofBits(Iterable<Integer> setBitIndexes) {
    int[] indexesArray =
        StreamSupport.stream(setBitIndexes.spliterator(), false).mapToInt(i -> i).toArray();
    return ofBits(indexesArray);
  }
}
