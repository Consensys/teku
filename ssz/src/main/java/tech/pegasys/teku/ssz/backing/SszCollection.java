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

package tech.pegasys.teku.ssz.backing;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;

public interface SszCollection<ElementT extends SszData>
    extends SszComposite<ElementT>, Iterable<ElementT> {

  default boolean isEmpty() {
    return size() == 0;
  }

  default Stream<ElementT> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  @NotNull
  @Override
  default Iterator<ElementT> iterator() {
    return new Iterator<>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public ElementT next() {
        return get(index++);
      }
    };
  }

  @Override
  SszCollectionSchema<ElementT, ?> getSchema();
}
