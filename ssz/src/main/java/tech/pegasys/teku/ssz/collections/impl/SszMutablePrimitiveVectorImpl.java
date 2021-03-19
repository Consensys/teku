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
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.ssz.impl.SszMutableVectorImpl;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszMutablePrimitiveVectorImpl<
        ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszMutableVectorImpl<SszElementT, SszElementT>
    implements SszMutablePrimitiveVector<ElementT, SszElementT> {

  private final SszPrimitiveSchema<ElementT, SszElementT> elementSchemaCache;

  @SuppressWarnings("unchecked")
  public SszMutablePrimitiveVectorImpl(AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchemaCache = (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  @Override
  public SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return elementSchemaCache;
  }

  @Override
  protected void validateChildSchema(int index, SszElementT value) {
    // no need to check primitive value schema
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveVector<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveVector<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  protected AbstractSszComposite<SszElementT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszElementT> childrenCache) {
    return new SszPrimitiveVectorImpl<>(getSchema(), backingNode);
  }

  @Override
  public SszMutablePrimitiveVector<ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
