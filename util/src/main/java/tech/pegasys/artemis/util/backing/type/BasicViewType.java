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

package tech.pegasys.artemis.util.backing.type;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;

public abstract class BasicViewType<C extends View> implements ViewType<C> {

  private final int bitsSize;

  public BasicViewType(int bitsSize) {
    this.bitsSize = bitsSize;
  }

  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public C createDefault() {
    return createFromTreeNode(new RootImpl(Bytes32.ZERO));
  }

  @Override
  public C createFromTreeNode(TreeNode node) {
    return createFromTreeNode(node, 0);
  }
}
