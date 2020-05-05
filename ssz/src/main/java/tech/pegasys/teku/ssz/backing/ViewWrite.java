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

package tech.pegasys.teku.ssz.backing;

import tech.pegasys.teku.ssz.backing.tree.TreeNode;

/**
 * Base class of mutable views over Binary Backing Tree ({@link TreeNode}) Each mutable View class
 * normally inherits from the corresponding immutable class to have both get/set methods however
 * ViewWrite instance shouldn't be leaked as ViewRead instance, the {@link #commitChanges()} should
 * be used instead to get immutable view
 */
public interface ViewWrite extends ViewRead {

  /** Resets this view to its default value */
  void clear();

  /** Creates the corresponding immutable structure with all the changes */
  ViewRead commitChanges();

  /**
   * This method may be not supported by mutable view implementation The general pattern for mutable
   * views to get backing node is <code>
   *   commit().getBackingNode()
   * </code>
   *
   * @throws UnsupportedOperationException is the method is not supported
   */
  @Override
  default TreeNode getBackingNode() {
    throw new UnsupportedOperationException(
        "Backing tree node should be accessed from ViewRead only");
  }
}
