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

import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.impl.SszMutableListImpl;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszMutablePrimitiveListImpl<
        ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszMutableListImpl<SszElementT, SszElementT>
    implements SszMutablePrimitiveList<ElementT, SszElementT> {

  private final SszPrimitiveSchema<ElementT, SszElementT> elementSchemaCache;

  @SuppressWarnings("unchecked")
  public SszMutablePrimitiveListImpl(
      SszPrimitiveListImpl<ElementT, SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchemaCache = (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  @Override
  public SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return elementSchemaCache;
  }

  @Override
  public void set(int index, SszElementT value) {
    // no need to check primitive value schema
    setUnsafe(index, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveList<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveListImpl<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  protected SszPrimitiveListImpl<ElementT, SszElementT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszElementT> childrenCache) {
    return new SszPrimitiveListImpl<>(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
