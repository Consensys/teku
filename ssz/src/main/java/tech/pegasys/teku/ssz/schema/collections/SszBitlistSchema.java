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

package tech.pegasys.teku.ssz.schema.collections;

import tech.pegasys.teku.ssz.collections.SszBitlist;
import tech.pegasys.teku.ssz.primitive.SszBit;
import tech.pegasys.teku.ssz.schema.collections.impl.SszBitlistSchemaImpl;

public interface SszBitlistSchema<SszBitlistT extends SszBitlist>
    extends SszPrimitiveListSchema<Boolean, SszBit, SszBitlistT> {

  static SszBitlistSchema<SszBitlist> create(long maxLength) {
    return new SszBitlistSchemaImpl(maxLength);
  }

  default SszBitlistT empty() {
    return ofBits(0);
  }

  SszBitlistT ofBits(int size, int... setBitIndexes);
}
