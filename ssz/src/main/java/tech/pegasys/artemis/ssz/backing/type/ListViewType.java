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

package tech.pegasys.artemis.ssz.backing.type;

import tech.pegasys.artemis.ssz.backing.view.ListViewReadImpl;
import tech.pegasys.artemis.ssz.backing.ListViewRead;
import tech.pegasys.artemis.ssz.backing.ViewRead;
import tech.pegasys.artemis.ssz.backing.tree.TreeNode;

public class ListViewType<C extends ViewRead> extends CollectionViewType {

  public ListViewType(VectorViewType<C> vectorType) {
    this(vectorType.getElementType(), vectorType.getMaxLength());
  }

  public ListViewType(ViewType elementType, long maxLength) {
    super(maxLength, elementType);
  }

  @Override
  protected TreeNode createDefaultTree() {
    return getDefault().getBackingNode();
  }

  @Override
  public ListViewRead<C> getDefault() {
    return new ListViewReadImpl<C>(this);
  }

  @Override
  public ListViewRead<C> createFromBackingNode(TreeNode node) {
    return new ListViewReadImpl<>(this, node);
  }

  public VectorViewType<C> getCompatibleVectorType() {
    return new VectorViewType<>(getElementType(), getMaxLength(), true);
  }
}
