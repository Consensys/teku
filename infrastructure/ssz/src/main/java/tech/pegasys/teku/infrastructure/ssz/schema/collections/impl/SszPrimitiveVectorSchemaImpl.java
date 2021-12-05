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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszPrimitiveVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveVectorSchemaImpl<
        ElementT,
        SszElementT extends SszPrimitive<ElementT, SszElementT>,
        SszVectorT extends SszPrimitiveVector<ElementT, SszElementT>>
    extends AbstractSszVectorSchema<SszElementT, SszVectorT>
    implements SszPrimitiveVectorSchema<ElementT, SszElementT, SszVectorT> {

  public SszPrimitiveVectorSchemaImpl(
      SszPrimitiveSchema<ElementT, SszElementT> elementSchema, long vectorLength) {
    super(elementSchema, vectorLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT createFromBackingNode(TreeNode node) {
    return (SszVectorT) new SszPrimitiveVectorImpl<ElementT, SszElementT>(this, node);
  }
}
