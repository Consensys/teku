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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBitvectorSchemaImpl;

public interface SszBitvectorSchema<SszBitvectorT extends SszBitvector>
    extends SszPrimitiveVectorSchema<Boolean, SszBit, SszBitvectorT> {

  static SszBitvectorSchema<SszBitvector> create(long length) {
    return new SszBitvectorSchemaImpl(length);
  }

  SszBitvectorT ofBits(int... setBitIndexes);

  default SszBitvectorT fromBytes(Bytes bivectorBytes) {
    return sszDeserialize(bivectorBytes);
  }

  default SszBitvectorT ofBits(Iterable<Integer> setBitIndexes) {
    int[] indexesArray =
        StreamSupport.stream(setBitIndexes.spliterator(), false).mapToInt(i -> i).toArray();
    return ofBits(indexesArray);
  }
}
