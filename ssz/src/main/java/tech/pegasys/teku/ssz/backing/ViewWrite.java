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
   * Returns the backing tree of this modified view Note that calling this method on {@link
   * ViewWrite} could be suboptimal from performance perspective as it internally needs to create an
   * immutable {@link ViewRead} via {@link #commitChanges()} which is then discarded. It's normally
   * better to make all modifications on {@link ViewWrite}, commit the changes and then call either
   * {@link ViewRead#getBackingNode()} or {@link ViewRead#hashTreeRoot()} on the resulting immutable
   * instance
   */
  @Override
  default TreeNode getBackingNode() {
    return commitChanges().getBackingNode();
  }
}
