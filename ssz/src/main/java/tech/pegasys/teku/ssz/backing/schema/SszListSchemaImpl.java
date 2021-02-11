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
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;

class SszListSchemaImpl<ElementDataT extends SszData>
    extends AbstractSszListSchema<ElementDataT, SszList<ElementDataT>> {

  public SszListSchemaImpl(SszSchema<ElementDataT> elementSchema, long maxLength) {
    super(elementSchema, maxLength);
  }

  public SszListSchemaImpl(
      SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
    super(elementSchema, maxLength, hints);
  }

  @Override
  public SszList<ElementDataT> createFromBackingNode(TreeNode node) {
    return new SszListImpl<>(this, node);
  }
}
