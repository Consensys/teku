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

package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.BasicView;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewType;

public abstract class AbstractBasicView<C, V extends AbstractBasicView<C, V>>
    implements BasicView<C> {
  private final BasicViewType<V> type;
  private final C value;

  public AbstractBasicView(C value, BasicViewType<V> type) {
    this.type = type;
    this.value = value;
  }

  @Override
  public C get() {
    return value;
  }

  @Override
  public BasicViewType<V> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return getType().createTreeNode(getThis());
  }

  @SuppressWarnings("unchecked")
  protected V getThis() {
    return (V) this;
  }
}
