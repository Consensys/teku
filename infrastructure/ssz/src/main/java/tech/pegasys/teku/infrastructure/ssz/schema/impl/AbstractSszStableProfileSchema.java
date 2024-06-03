/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableProfileSchema<C extends SszContainer>
    extends AbstractSszStableContainerSchema<C> {

  public AbstractSszStableProfileSchema(
      String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
    super(name, childrenSchemas, maxFieldCount);
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    return super.sszSerializeParentTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    return super.sszDeserializeParentTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getParentSszLengthBounds();
  }

  @Override
  public int getSszSize(TreeNode node) {
    return super.getParentSszSize(node);
  }
}
