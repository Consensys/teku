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

package tech.pegasys.teku.ssz.collections.impl;

import java.util.function.Supplier;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.ssz.impl.SszListImpl;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszPrimitiveListImpl<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszListImpl<SszElementT> implements SszPrimitiveList<ElementT, SszElementT> {

  public SszPrimitiveListImpl(
      SszPrimitiveListSchema<ElementT, SszElementT, ?> schema, Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  public SszPrimitiveListImpl(SszListSchema<SszElementT, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszPrimitiveListImpl(
      SszListSchema<SszElementT, ?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    return new SszMutablePrimitiveListImpl<>(this);
  }
}
