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

import java.util.Map;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableProfileSchema<C extends SszStableContainer>
    extends AbstractSszStableContainerSchema<C> {

  public AbstractSszStableProfileSchema(
      String name, Map<Integer, NamedSchema<?>> childrenSchemas, int maxFieldCount) {
    super(name, childrenSchemas, maxFieldCount);
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    return super.sszSerializeTreeAsProfile(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    return super.sszDeserializeTreeAsProfile(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBoundsAsProfile();
  }

  @Override
  public int getSszSize(TreeNode node) {
    return super.getSszSizeAsProfile(node);
  }
}
