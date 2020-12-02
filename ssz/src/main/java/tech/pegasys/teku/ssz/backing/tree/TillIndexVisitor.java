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

package tech.pegasys.teku.ssz.backing.tree;

import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;

class TillIndexVisitor implements TreeVisitor {

  static TreeVisitor create(TreeVisitor delegate, long tillGeneralizedIndex) {
    return new TillIndexVisitor(delegate, tillGeneralizedIndex, true);
  }

  private final TreeVisitor delegate;
  private final long tillGIndex;
  private final boolean inclusive;

  public TillIndexVisitor(TreeVisitor delegate, long tillGIndex, boolean inclusive) {
    this.delegate = delegate;
    this.tillGIndex = tillGIndex;
    this.inclusive = inclusive;
  }

  @Override
  public boolean visit(TreeNode node, long generalizedIndex) {
    NodeRelation compareRes = GIndexUtil.gIdxCompare(generalizedIndex, tillGIndex);
    if (inclusive && compareRes == NodeRelation.Right) {
      return false;
    } else if (!inclusive && (compareRes == NodeRelation.Same)) {
      return false;
    } else {
      return delegate.visit(node, generalizedIndex);
    }
  }
}
