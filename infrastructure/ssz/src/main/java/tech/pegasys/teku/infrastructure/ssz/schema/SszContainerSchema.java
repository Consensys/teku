/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.List;
import java.util.function.BiFunction;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.LoadingUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

/**
 * {@link SszSchema} for an Ssz Container structure
 *
 * @param <C> the type of actual container class
 */
public interface SszContainerSchema<C extends SszContainer> extends SszCompositeSchema<C> {

  /**
   * Creates a new {@link SszContainer} schema with specified field schemas and container instance
   * constructor
   */
  static <C extends SszContainer> SszContainerSchema<C> create(
      List<SszSchema<?>> childrenSchemas,
      BiFunction<SszContainerSchema<C>, TreeNode, C> instanceCtor) {
    return new AbstractSszContainerSchema<C>(childrenSchemas) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  static <C extends SszContainer> SszContainerSchema<C> create(
      String containerName,
      List<NamedSchema<?>> childrenSchemas,
      BiFunction<SszContainerSchema<C>, TreeNode, C> instanceCtor) {
    return new AbstractSszContainerSchema<C>(containerName, childrenSchemas) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  /**
   * Get the index of a field by name
   *
   * @param fieldName
   * @return The index if it exists, otherwise -1
   */
  int getFieldIndex(String fieldName);

  /**
   * Creates the backing tree from container field values
   *
   * @throws IllegalArgumentException if value types doesn't match this scheme field types
   */
  TreeNode createTreeFromFieldValues(List<? extends SszData> fieldValues);

  /**
   * Creates an {@link SszContainer} instance from field values
   *
   * @throws IllegalArgumentException if value types doesn't match this scheme field types
   */
  default C createFromFieldValues(List<? extends SszData> fieldValues) {
    return createFromBackingNode(createTreeFromFieldValues(fieldValues));
  }

  /** Returns the number of fields in ssz containers of this type */
  default int getFieldsCount() {
    return (int) getMaxLength();
  }

  @Override
  default void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(gIndex, treeDepth());
    final SszSchema<?> childSchema = getChildSchema(childIndex);
    childSchema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  @Override
  default TreeNode loadBackingNodes(TreeNodeSource nodeSource, Bytes32 rootHash, long rootGIndex) {
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, treeDepth());
    return LoadingUtil.loadNodesToDepth(
        nodeSource,
        rootHash,
        rootGIndex,
        treeDepth(),
        getDefault().getBackingNode(),
        lastUsefulGIndex,
        this::loadChildNode);
  }

  private TreeNode loadChildNode(
      final TreeNodeSource nodeSource, final Bytes32 childHash, final long childGIndex) {
    final int childIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth());
    return getChildSchema(childIndex).loadBackingNodes(nodeSource, childHash, childGIndex);
  }

  /** Returns this container name */
  String getContainerName();

  /** Return this container field names */
  List<String> getFieldNames();

  /** Return this container field schemas */
  List<SszSchema<?>> getFieldSchemas();
}
