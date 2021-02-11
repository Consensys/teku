/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszVectorImpl;

class SszVectorSchemaImpl<SszElementT extends SszData>
    extends AbstractSszVectorSchema<SszElementT, SszVector<SszElementT>> {

  SszVectorSchemaImpl(SszSchema<SszElementT> elementType, long vectorLength) {
    this(elementType, vectorLength, false, SszSchemaHints.none());
  }

  SszVectorSchemaImpl(
      SszSchema<SszElementT> elementSchema,
      long vectorLength,
      boolean isListBacking,
      SszSchemaHints hints) {
    super(elementSchema, vectorLength, isListBacking, hints);
  }

  @Override
  public SszVector<SszElementT> createFromBackingNode(TreeNode node) {
    return new SszVectorImpl<>(this, node);
  }
}
