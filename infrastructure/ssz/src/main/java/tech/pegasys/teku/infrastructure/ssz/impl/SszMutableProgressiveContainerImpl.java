/*
 * Copyright Consensys Software Inc., 2026
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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;

/**
 * Mutable implementation of SszContainer backed by a progressive merkle tree.
 *
 * <p>Overrides the standard change-to-tree-update flow because progressive trees have mixed-depth
 * generalized indices. Field changes are mapped to tree-position slot changes and applied via
 * {@link ProgressiveTreeUtil#updateProgressiveTree}.
 */
public class SszMutableProgressiveContainerImpl
    extends AbstractSszMutableComposite<SszData, SszMutableData> implements SszMutableRefContainer {

  private List<Map.Entry<Integer, SszData>> pendingChanges;

  public SszMutableProgressiveContainerImpl(
      final SszProgressiveContainerImpl backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected TreeUpdates changesToNewNodes(
      final Stream<Map.Entry<Integer, SszData>> newChildValues, final TreeNode original) {
    pendingChanges = newChildValues.toList();
    return new TreeUpdates(List.of(), List.of());
  }

  @Override
  protected TreeNode doFinalTreeUpdates(final TreeNode tree) {
    if (pendingChanges == null || pendingChanges.isEmpty()) {
      return tree;
    }

    SszProgressiveContainerSchema<?> schema = getSchema();
    TreeNode dataTree = tree.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    TreeNode activeFieldsNode = tree.get(GIndexUtil.RIGHT_CHILD_G_INDEX);

    // Map field indices to tree-position slots
    Int2ObjectMap<TreeNode> slotUpdates = new Int2ObjectOpenHashMap<>();
    for (Map.Entry<Integer, SszData> entry : pendingChanges) {
      int fieldIndex = entry.getKey();
      int treePosition = schema.getTreePosition(fieldIndex);
      slotUpdates.put(treePosition, entry.getValue().getBackingNode());
    }

    int totalSlots = schema.getActiveFields().length;

    TreeNode updatedDataTree =
        ProgressiveTreeUtil.updateProgressiveTree(dataTree, slotUpdates, totalSlots);

    pendingChanges = null;
    return BranchNode.create(updatedDataTree, activeFieldsNode);
  }

  @Override
  protected SszProgressiveContainerImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszData> viewCache) {
    return new SszProgressiveContainerImpl(getSchema(), backingNode, viewCache);
  }

  @Override
  public SszProgressiveContainerSchema<?> getSchema() {
    return (SszProgressiveContainerSchema<?>) super.getSchema();
  }

  @Override
  public SszContainer commitChanges() {
    return (SszContainer) super.commitChanges();
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable SszData only");
  }

  @Override
  protected void checkIndex(final int index, final boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive container with size " + size());
    }
  }
}
