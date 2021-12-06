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
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszPrimitiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveListSchemaImpl<
        ElementT,
        SszElementT extends SszPrimitive<ElementT, SszElementT>,
        SszListT extends SszPrimitiveList<ElementT, SszElementT>>
    extends AbstractSszListSchema<SszElementT, SszListT>
    implements SszPrimitiveListSchema<ElementT, SszElementT, SszListT> {

  public SszPrimitiveListSchemaImpl(
      SszPrimitiveSchema<ElementT, SszElementT> elementSchema, long maxLength) {
    super(elementSchema, maxLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(TreeNode node) {
    return (SszListT) new SszPrimitiveListImpl<>(this, node);
  }
}
