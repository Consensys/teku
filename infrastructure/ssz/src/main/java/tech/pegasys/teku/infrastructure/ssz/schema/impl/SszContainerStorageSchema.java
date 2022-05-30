/*
 * Copyright 2022 ConsenSys AG.
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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates.Update;

public class SszContainerStorageSchema<C extends SszContainer>
    extends AbstractSszContainerSchema<SszContainerStorage<C>> {

  private final AbstractSszContainerSchema<C> fullSchema;
  private final IntList ommittedChildren;

  public SszContainerStorageSchema(
      final String containerName,
      final AbstractSszContainerSchema<C> fullSchema,
      final List<NamedSchema<?>> childSchemas,
      final IntList ommittedChildren) {
    super(containerName, childSchemas);
    this.fullSchema = fullSchema;
    this.ommittedChildren = ommittedChildren;
  }

  @Override
  public SszContainerStorage<C> createFromBackingNode(final TreeNode node) {
    return new SszContainerStorage<>(this, node);
  }

  public SszContainerStorage<C> createFromFullVersion(
      final C value, final Consumer<SszData> separateStorage) {
    final List<TreeUpdates.Update> updates = new ArrayList<>();

    ommittedChildren.forEach(
        index -> {
          final SszData childData = value.get(index);
          updates.add(
              new Update(
                  getChildGeneralizedIndex(index),
                  SszBytes32.of(childData.hashTreeRoot()).getBackingNode()));
          separateStorage.accept(childData);
        });

    final TreeNode storageTree = value.getBackingNode().updated(new TreeUpdates(updates));
    return createFromBackingNode(storageTree);
  }

  public C loadFully(
      final Function<Bytes32, Bytes> partLoader, final SszContainerStorage<C> storedContainer) {
    final List<TreeUpdates.Update> updates = new ArrayList<>();
    ommittedChildren.forEach(
        index -> {
          final SszBytes32 root = storedContainer.getAny(index);
          final Bytes partSszData = partLoader.apply(root.get());
          final TreeNode childTreeNode =
              fullSchema.getChildSchema(index).sszDeserializeTree(SszReader.fromBytes(partSszData));
          updates.add(new TreeUpdates.Update(getChildGeneralizedIndex(index), childTreeNode));
        });

    final TreeNode fullTree = storedContainer.getBackingNode().updated(new TreeUpdates(updates));
    return fullSchema.createFromBackingNode(fullTree);
  }
}
