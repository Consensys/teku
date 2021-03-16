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

import java.util.AbstractList;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;

public interface SszPrimitiveCollection<
        ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszCollection<SszElementT> {

  default ElementT getElement(int index) {
    return get(index).get();
  }

  @Override
  SszCollectionSchema<SszElementT, ?> getSchema();

  @Override
  SszMutablePrimitiveCollection<ElementT, SszElementT> createWritableCopy();

  default List<ElementT> asListUnboxed() {
    return new AbstractList<>() {
      @Override
      public ElementT get(int index) {
        return SszPrimitiveCollection.this.getElement(index);
      }

      @Override
      public int size() {
        return SszPrimitiveCollection.this.size();
      }
    };
  }

  default Stream<ElementT> streamUnboxed() {
    return asListUnboxed().stream();
  }
}
