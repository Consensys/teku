/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.impl;

import java.util.NoSuchElementException;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszMutableStableContainerBaseImpl extends SszMutableContainerImpl {
  protected SszStableContainerBaseImpl backingStableContainerBaseView;

  public SszMutableStableContainerBaseImpl(final SszStableContainerBaseImpl backingImmutableView) {
    super(backingImmutableView);
    this.backingStableContainerBaseView = backingImmutableView;
  }

  @Override
  protected SszContainerImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszData> viewCache) {
    return new SszStableContainerBaseImpl(
        getSchema().toStableContainerSchemaBaseRequired(), backingNode, viewCache);
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    // we currently not support StableContainers with optional fields, so we expect get\set over an
    // already active field
    if (backingStableContainerBaseView.isFieldActive(index)) {
      return;
    }

    if (index > backingStableContainerBaseView.getActiveFields().getLastSetBitIndex()) {
      throw new IndexOutOfBoundsException(
          "Invalid index "
              + index
              + " for container with last active index "
              + backingStableContainerBaseView.getActiveFields().getLastSetBitIndex());
    }

    throw new NoSuchElementException("Index " + index + " is not active in the stable container");
  }

  @Override
  protected int calcNewSize(final int index) {
    // StableContainer have sparse index so size cannot be compared with index.
    // Currently, the only mutable StableContainer we support is a Profile with no optional
    // fields, so we can assume the size never changes.
    // See:
    // tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerBaseImpl.createWritableCopy
    return size();
  }

  @Override
  public String toString() {
    return "Mutable " + backingStableContainerBaseView.toString();
  }
}
