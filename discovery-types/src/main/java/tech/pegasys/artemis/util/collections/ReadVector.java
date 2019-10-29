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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface ReadVector<IndexType extends Number, ValueType>
    extends ReadList<IndexType, ValueType> {

  /** Wraps with creating of new vector */
  static <IndexType extends Number, ValueType> ReadVector<IndexType, ValueType> wrap(
      List<ValueType> srcList, Function<Integer, IndexType> indexConverter) {
    return ListImpl.wrap(new ArrayList<>(srcList), indexConverter, true);
  }

  /** Wraps with verifying of length and creating new vector */
  static <IndexType extends Number, ValueType> ReadVector<IndexType, ValueType> wrap(
      List<ValueType> srcList, Function<Integer, IndexType> indexConverter, int vectorLength) {
    assert srcList.size() == vectorLength;
    return ListImpl.wrap(new ArrayList<>(srcList), indexConverter, true);
  }

  default ReadVector<IndexType, ValueType> vectorCopy() {
    ReadVector<IndexType, ValueType> res = createMutableCopy();
    return res;
  }
}
